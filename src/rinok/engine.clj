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
  [o sb cbs]
  (let [top (book/top sb)
        can-trade? (if (sell? o)
                     (<= (:threshold o) (:threshold top))
                     (>= (:threshold o) (:threshold top)))
        min-quantity (min (:quantity o) (:quantity top))
        remaining-quantity (- (:quantity o) min-quantity)]
    (if can-trade?
      ;; if: Trade happens
      (do
        (book/decrement sb min-quantity)
        (doseq [cb cbs]
          (if (sell? o)
            (cb :trade (trade (:account top) (:account o) (:threshold top) min-quantity))
            (cb :trade (trade (:account o) (:account top) (:threshold o) min-quantity))))
        (when (pos? remaining-quantity)
          ;; Not tail-rec optimized
          (match (assoc-in o [:quantity] remaining-quantity) sb cbs)))
      ;; else: No trade happens
      o)))

(defprotocol IMatchingEngine
  (accept [_ o] "Accept an order to the engine")
  (subscribe [_ cb] "Register a callback for events"))

(defn ->MatchingEngine []
  (let [buy-book (book/->OrderBook (atom book/buy-map))
        sell-book (book/->OrderBook (atom book/sell-map))
        callbacks (atom [])]
    (reify IMatchingEngine
      (accept [_ o]
        (let [opposite-book (if (sell? o) buy-book sell-book)
              type-book (if (sell? o) sell-book buy-book)]
          (if (nil? (book/top opposite-book))
            (book/accept type-book o)
            (let [pending (match o opposite-book @callbacks)]
              ;; Add what's ever left over to the type's book
              (when-not (nil? pending)
                (book/accept type-book pending))))))
      
      (subscribe [_ cb]
        (swap! callbacks conj cb)))))
