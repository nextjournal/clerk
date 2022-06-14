(ns hash-fn
  (:require [nextjournal.clerk :as clerk]))

^{::clerk/hash-fn (fn [_] (clerk/valuehash (slurp "notebooks/hello.clj")))}
(def contents
  (slurp "notebooks/hello.clj"))

(defonce !state (atom 0))

(-> @!state inc dec)

@!state

(-> @!state inc dec)


#_(do (swap! !state inc)
      (clerk/recompute!))
