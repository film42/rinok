(ns rinok.book-test
  (:require [clojure.test :refer :all]
            [rinok.book :refer :all]))

(defn order-book []
  (->OrderBook (atom {}) :sell))

(deftest top-of-the-book
  (testing "will get nil when the book is empty"
    (let [book (order-book)]
      (is (nil? (top book)))))

  (testing "will get order when a book is on top"
    (let [book (order-book)
          order {:quantity 100 :theshold 10.1}]
      (accept book order)
      (is (= order (top book))))))

(deftest decrementing-book
  (testing "decrement exactly whats on top with one order at threshold"
    (let [book (order-book)]
      (accept book {:quantity 100 :threshold 10.1})
      (accept book {:quantity 400 :threshold 10.2})
      (is (= {:quantity 100 :threshold 10.1} (top book)))
      (decrement book 100)
      (is (= {:quantity 400 :threshold 10.2} (top book)))))

  (testing "decrement exactly whats on top with two orders at threshold"
    (let [book (order-book)]
      (accept book {:quantity 100 :threshold 10.1})
      (accept book {:quantity 200 :threshold 10.1})
      (accept book {:quantity 400 :threshold 10.2})
      (is (= {:quantity 100 :threshold 10.1} (top book)))
      (decrement book 100)
      (is (= {:quantity 200 :threshold 10.1} (top book)))))

  (testing "decrement with a top of nil"
    (let [book (order-book)]
      (decrement book 1000)
      (is (= nil (top book)))))

  (testing "decrement more than top into second"
    (let [book (order-book)]
      (accept book {:quantity 100 :threshold 10.1})
      (accept book {:quantity 200 :threshold 10.1})
      (decrement book 160)
      (is (= {:quantity 140 :threshold 10.1}))))

  (testing "decrement partially from top"
    (let [book (order-book)]
      (accept book {:quantity 100 :threshold 10.1})
      (decrement book 60)
      (is (= {:quantity 40 :threshold 10.1 })))))
