(ns rinok.engine-test
  (:require [clojure.test :refer :all]
            [rinok.engine :as eng]))

(deftest matching-sells-to-buys
  (testing "can match three sell orders to three buy orders"
    (let [engine (eng/->MatchingEngine)
          state (atom [])]
      ;; Register event callback
      (eng/subscribe engine
                     (fn [t m] (swap! state conj m)))

      ;; Run tests
      (eng/accept engine (eng/limit-order 'A 10.5 200 :buy))
      (eng/accept engine (eng/limit-order 'B 10.6 100 :buy))
      (eng/accept engine (eng/limit-order 'C 10.4 200 :sell))
      (eng/accept engine (eng/limit-order 'D 10.3 100 :sell))
      (eng/accept engine (eng/limit-order 'D 10.3 100 :sell))
      (eng/accept engine (eng/limit-order 'E 10.7 200 :buy))

      ;; Check results
      (is (= @state [{:buyer 'B, :seller 'C, :price 10.6, :quantity 100}
                     {:buyer 'A, :seller 'C, :price 10.5, :quantity 100}
                     {:buyer 'A, :seller 'D, :price 10.5, :quantity 100}
                     {:buyer 'E, :seller 'D, :price 10.7, :quantity 100}]))))

  (testing "can match concurrently"
    (let [engine1 (eng/->MatchingEngine)
          engine2 (eng/->MatchingEngine)
          state1 (atom [])
          state2 (atom [])]
      ;; Register event callback
      (eng/subscribe engine1
                     (fn [t m] (swap! state1 conj m)))
      (eng/subscribe engine2
                     (fn [t m] (swap! state2 conj m)))

      ;; Run tests
      (let [orders [(eng/limit-order 'A 10.5 200 :buy)
                    (eng/limit-order 'B 10.6 100 :buy)
                    (eng/limit-order 'C 10.4 200 :sell)
                    (eng/limit-order 'D 10.3 100 :sell)
                    (eng/limit-order 'D 10.3 100 :sell)
                    (eng/limit-order 'E 10.7 200 :buy)]
            random-orders (atom [])]

        (doall
         (pmap #(do
                  (eng/accept engine1 %)
                  ;; Record order in the order they were called by pmap
                  (swap! random-orders conj %)) orders))

        (doall (map #(eng/accept engine2 %) @random-orders)))

      ;; Check results
      (is (= @state1 @state2))))


  (testing "can match two sell orders to two buy orders"
    (let [engine (eng/->MatchingEngine)
          state (atom [])]
      ;; Register event callback
      (eng/subscribe engine
                     (fn [t m] (swap! state conj m)))

      ;; Run tests
      (eng/accept engine (eng/limit-order 'C 10.4 200 :sell))
      (eng/accept engine (eng/limit-order 'D 10.3 100 :sell))
      (eng/accept engine (eng/limit-order 'B 10.6 100 :buy))
      (eng/accept engine (eng/limit-order 'A 10.5 200 :buy))

      ;; Check results
      (is (= @state [{:buyer 'B, :seller 'D, :price 10.6, :quantity 100}
                     {:buyer 'A, :seller 'C, :price 10.5, :quantity 200}]))))

  (testing "can match a bunch of really large orders"
    (let [engine (eng/->MatchingEngine)
          state (atom [])]
      ;; Register event callback
      (eng/subscribe engine
                     (fn [t m] (swap! state conj m)))

      ;; Run tests
      (doseq [_ (range 1000)]
        (eng/accept engine (eng/limit-order 'A 10.5 20000 :buy))
        (eng/accept engine (eng/limit-order 'B 10.6 10000 :buy))
        (eng/accept engine (eng/limit-order 'C 10.4 20000 :sell))
        (eng/accept engine (eng/limit-order 'D 10.3 10000 :sell))
        (eng/accept engine (eng/limit-order 'D 10.3 10000 :sell))
        (eng/accept engine (eng/limit-order 'E 10.7 20000 :buy)))

      ;; Check trades
      (is (= 3002 (count @state))))))
