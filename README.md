Rinok
=====

A very simple matching engine written in Clojure

#### Usage

You can clone the project and run: `lein run`

Here's a very simple example:

```clojure
(ns rinok.core
  (:require [rinok.engine :as eng]))

(defn -main []
  (let [engine (eng/->MatchingEngine)]
    ;; Register event callback
    (eng/register-cb engine println)

    (eng/accept engine (eng/limit-order 'A 10.5 200 :buy))
    (eng/accept engine (eng/limit-order 'B 10.6 100 :buy))
    (eng/accept engine (eng/limit-order 'C 10.4 200 :sell))
    (eng/accept engine (eng/limit-order 'D 10.3 100 :sell))))
```

Which would generate the following events:

```clojure
:trade {:buyer B, :seller C, :price 10.6, :quantity 100}
:trade {:buyer A, :seller C, :price 10.5, :quantity 100}
:trade {:buyer A, :seller D, :price 10.5, :quantity 100}
```

#### Testing

To run the unit tests, clone the project and run: `lein test`
