(ns rinok.core
  (:require [rinok.engine :as eng]))

(defn -main []
  (println "Starting engine...")
  (let [engine (eng/->MatchingEngine)]
    ;; Register event callback
    (eng/subscribe engine println)

    (eng/accept engine (eng/limit-order 'A 10.5 200 :buy))
    (eng/accept engine (eng/limit-order 'B 10.6 100 :buy))
    (eng/accept engine (eng/limit-order 'C 10.4 200 :sell))
    (eng/accept engine (eng/limit-order 'D 10.3 100 :sell))))
