(ns rinok.book)

(def sell-map (sorted-map-by <))
(def buy-map (sorted-map-by >))
(defn- sell? [t] (= :sell t))

(defprotocol IOrderBook
  "An Order Book for accepting Orders"
  (accept [_ o] "Accept an order")
  (top [_] "See the top of the book")
  (decrement [_ quantity] "Decrement the book by"))

(defrecord OrderBook [state]
  IOrderBook
  (accept [_ o]
    (let [threshold (:threshold o)]
      ;; We use ordering to control sort-by on keys. A buy book should be
      ;; sorted from high->low, and a sell book should be low->high.
      (swap! state #(assoc % threshold (conj (if (nil? (% threshold))
                                               []
                                               (% threshold)) o)))))
  (top [_] (-> @state first last first))
  (decrement [_ quantity]
    (loop [remaining quantity]
      (when-not (nil? (top _))
        (let [quantity (:quantity (top _))
              threshold (:threshold (top _))
              difference (- remaining quantity)]

          (if (neg? difference)
            ;; Partial top removal
            (swap! state #(assoc-in % [threshold 0 :quantity]
                                    (Math/abs difference)))
            ;; Complete top and let loop
            (do
              (swap! state #(assoc-in % [threshold] (vec (rest (% threshold)))))
              (when (nil? (top _))
                ;; Top is nil, meaning, the threshold has no more orders
                (swap! state dissoc threshold))))

          ;; Recur until finished unless zero
          (if (or (neg? difference) (zero? difference))
            @state
            (recur difference)))))))

