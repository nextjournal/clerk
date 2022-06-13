(ns hash-fn
  (:require [nextjournal.clerk :as clerk]))

^{::clerk/hash-fn (constantly (slurp "notebooks/hello.clj"))}
(def contents
  (slurp "notebooks/hello.clj"))

(defonce !state (atom 0))

^{::clerk/hash-fn (constantly @!state)}
(-> @!state inc dec)

@!state

(-> @!state inc dec)

(defonce !state-2 (atom 0))

(-> @!state-2 inc dec)

@!state-2

#_(do (swap! !state inc)
      (clerk/recompute!))
