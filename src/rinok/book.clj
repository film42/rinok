(ns rinok.book)

(defn- sell? [t] (= :sell t))

(defprotocol IOrderBook
  "An Order Book for accepting Orders"
  (accept [_ o] "Accept an order")
  (top [_] "See the top of the book")
  (decrement [_ quantity] "Decrement the book by"))

(defrecord OrderBook [state type]
  IOrderBook
  (accept [_ o]
    (let [threshold (:threshold o)
          ordering (if (sell? type) < >)]
      ;; We use ordering to control sort-by on keys. A buy book should be
      ;; sorted from high->low, and a sell book should be low->high.
      (swap! state #(into (sorted-map-by ordering)
                          (assoc % threshold (conj (vec (% threshold)) o))))))
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

