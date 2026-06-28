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
                     {:buyer 'E, :seller 'D, :price 10.3, :quantity 100}]))))

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
      (is (= @state [{:buyer 'B, :seller 'D, :price 10.3, :quantity 100}
                     {:buyer 'A, :seller 'C, :price 10.4, :quantity 200}]))))

  (testing "buy aggressor prices fill at resting sell's price (maker price)"
    (let [engine (eng/->MatchingEngine)
          trades (atom [])]
      (eng/subscribe engine (fn [_ t] (swap! trades conj t)))
      (eng/accept engine (eng/limit-order 'M 10.3 100 :sell))   ; resting maker @ 10.3
      (eng/accept engine (eng/limit-order 'A 10.7 100 :buy))    ; aggressor   @ 10.7
      (is (= @trades [{:buyer 'A, :seller 'M, :price 10.3, :quantity 100}]))))

  (testing "sell aggressor prices fill at resting buy's price (maker price)"
    (let [engine (eng/->MatchingEngine)
          trades (atom [])]
      (eng/subscribe engine (fn [_ t] (swap! trades conj t)))
      (eng/accept engine (eng/limit-order 'M 10.7 100 :buy))    ; resting maker @ 10.7
      (eng/accept engine (eng/limit-order 'A 10.3 100 :sell))   ; aggressor   @ 10.3
      (is (= @trades [{:buyer 'M, :seller 'A, :price 10.7, :quantity 100}]))))

  (testing "non-crossing order rests on the book without generating a trade"
    (let [engine (eng/->MatchingEngine)
          trades (atom [])]
      (eng/subscribe engine (fn [_ t] (swap! trades conj t)))
      (eng/accept engine (eng/limit-order 'A 10.5 100 :buy))
      (eng/accept engine (eng/limit-order 'B 10.6 100 :sell))  ; sell at 10.6 doesn't cross buy at 10.5
      (is (= @trades []))))

  (testing "aggressive order sweeps multiple price levels"
    (let [engine (eng/->MatchingEngine)
          trades (atom [])]
      (eng/subscribe engine (fn [_ t] (swap! trades conj t)))
      (eng/accept engine (eng/limit-order 'A 10.1 100 :sell))
      (eng/accept engine (eng/limit-order 'B 10.2 100 :sell))
      (eng/accept engine (eng/limit-order 'C 10.3 100 :sell))
      (eng/accept engine (eng/limit-order 'D 10.5 250 :buy))   ; sweeps A and B fully, C partially
      (is (= @trades [{:buyer 'D, :seller 'A, :price 10.1, :quantity 100}
                       {:buyer 'D, :seller 'B, :price 10.2, :quantity 100}
                       {:buyer 'D, :seller 'C, :price 10.3, :quantity 50}]))))

  (testing "partial fill leaves remainder on the book"
    (let [engine (eng/->MatchingEngine)
          trades (atom [])]
      (eng/subscribe engine (fn [_ t] (swap! trades conj t)))
      (eng/accept engine (eng/limit-order 'A 10.5 200 :sell))
      (eng/accept engine (eng/limit-order 'B 10.5 50 :buy))    ; only fills 50 of 200
      (eng/accept engine (eng/limit-order 'C 10.5 50 :buy))    ; fills next 50 from the remaining 150
      (is (= @trades [{:buyer 'B, :seller 'A, :price 10.5, :quantity 50}
                       {:buyer 'C, :seller 'A, :price 10.5, :quantity 50}]))))

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
