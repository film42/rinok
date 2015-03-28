(ns rinok.benchmark
  (:require [rinok.engine :as eng]))

(defn set-interval [ms callback]
  (future (while true (do (Thread/sleep ms) (callback)))))

(defn random-order []
  (let [side (rand-nth [:buy :sell])
        amount (+ (rand-int 1000) (* 0.1 (rand-int 10)))
        quantity (rand-int 10000)]
    (eng/limit-order 'A amount quantity side)))

(defn -main []
  (println "Starting engine...")
  (let [engine (eng/->MatchingEngine)
        state (atom [])
        order-count (atom 0)]

    ;; Register event callback
    (eng/subscribe engine
                   (fn [t m] (swap! state conj m)))

    ;; Add tons of orders to the engine using 10 threads
    (dotimes [_ 10]
      (future
        (while true
          (do
            (eng/accept engine (random-order))
            (swap! order-count inc)))))

    ;; Print throughput every second
    (set-interval 1000
     #(do
        (println "Trades per second:" (count @state))
        (reset! state [])

        (println "Orders per second:" @order-count)
        (reset! order-count 0)

        (println "-------------------------")))))
