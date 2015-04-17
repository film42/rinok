(ns rinok.engine
  (:require [rinok.book :as book]))

(defn limit-order
  "Example: (order 'A 10.5 100 :sell)"
  [account threshold quantity type]
  {:account account
   :threshold threshold
   :quantity quantity
   :type type})

(defn trade [buyer seller price quantity]
  {:buyer buyer
   :seller seller
   :price price
   :quantity quantity})

(defn- sell? [o] (= :sell (:type o)))
(defn- buy? [o] (= :buy (:type o)))

(defn- match
  "Attempt to match an order against book"
  [o b cbs]
  (loop [o o]
    (let [top (book/top b)
          can-trade? (cond
                      (nil? top) false
                      (sell? o) (<= (:threshold o) (:threshold top))
                      (buy? o) (>= (:threshold o) (:threshold top)))]
      (if can-trade?
        (let [min-quantity (min (:quantity o) (:quantity top))
              remaining-quantity (- (:quantity o) min-quantity)]
          ;; if: Trade happens
          (do
            (book/decrement b min-quantity)
            (doseq [cb cbs]
              (if (sell? o)
                (cb :trade (trade (:account top) (:account o) (:threshold top) min-quantity))
                (cb :trade (trade (:account o) (:account top) (:threshold o) min-quantity))))
            (when (pos? remaining-quantity)
              (recur (assoc-in o [:quantity] remaining-quantity)))))
        ;; else: No trade happens
        o))))

(defprotocol IMatchingEngine
  (accept [_ o] "Accept an order to the engine")
  (subscribe [_ cb] "Register a callback for events"))

(defrecord MatchingEngine [buy-book sell-book callbacks]
  IMatchingEngine
  (accept [_ o]
    (locking :always
      (let [opposite-book (if (sell? o) buy-book sell-book)
            type-book (if (sell? o) sell-book buy-book)
            pending (match o opposite-book @callbacks)]
        (when-not (nil? pending)
          ;; Add what's ever left over to the type's book
          (book/accept type-book pending)))))

  (subscribe [_ cb]
    (swap! callbacks conj cb)))

;; HACK: Override `new' method to use default parameters
(defn ->MatchingEngine []
  (let [buy-book (book/->OrderBook (atom book/buy-map))
        sell-book (book/->OrderBook (atom book/sell-map))
        callbacks (atom [])]
    (MatchingEngine. buy-book sell-book callbacks)))
