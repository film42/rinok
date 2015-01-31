(ns rinok.core
  (:require [rinok.engine :as eng]))

(defn -main []
  (println "Starting engine...")
  (let [engine (eng/->MatchingEngine)]
    ;; Register event callback
    (eng/register-cb engine println)

    (eng/accept engine (eng/order 'A 10.5 200 :buy))
    (eng/accept engine (eng/order 'B 10.6 100 :buy))
    (eng/accept engine (eng/order 'C 10.4 200 :sell))
    (eng/accept engine (eng/order 'D 10.3 100 :sell))))
