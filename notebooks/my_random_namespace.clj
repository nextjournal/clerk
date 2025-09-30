(ns my-random-namespace
  (:require [fixture-ns]))

;; this function should be run during macro pre-analysis
(defn helper-fn-compile-time [x] x)

(defn helper-fn-runtime [x] x)

(defmacro attempt1
  [x]
  (helper-fn-compile-time
   `(try
      (do (helper-fn-runtime ~x))
      (catch Exception e# e#))))

;; a1 has dependency on helper-fn-runtime, but this isn't clear when we don't macro-expand before analysis
;; if we don't, then a1 gets a different hash compared to the next time and we get twice the side effects
(def a1 (do
          (swap! fixture-ns/state inc)
          (rand-int (attempt1 9999))))

@fixture-ns/state

#_(do (reset! fixture-ns/state 0)
      (remove-ns 'my-random-namespace)
      (nextjournal.clerk/clear-cache!)
      (create-ns 'my-random-namespace))
