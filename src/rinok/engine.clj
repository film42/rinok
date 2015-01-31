(ns rinok.engine
  (:require [rinok.book :as book]))

(defn limit-order
  "Example: (order 10.5 100 :sell)"
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
          (cb :trade
              (trade
               (if (buy? o) (:account o) (:account top))
               (if (sell? o) (:account o) (:account top))
               (if (sell? o)
                 ;; Take the higher price if a sell order
                 (:threshold top)
                 ;; Take the lower price if a buy order
                 (:threshold o)) min-quantity)))
        (when (pos? remaining-quantity)
          ;; Not tail-rec optimized
          (match (assoc-in o [:quantity] remaining-quantity) sb cbs)))
      ;; else: No trade happens
      o)))

(defprotocol IMatchingEngine
  (accept [_ o] "Accept an order to the engine")
  (register-cb [_ cb] "Register a callback for events"))

(defn ->MatchingEngine []
  (let [buy-book (book/->OrderBook (atom {}) :buy)
        sell-book (book/->OrderBook (atom {}) :sell)
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
      
      (register-cb [_ cb]
        (swap! callbacks conj cb)))))
