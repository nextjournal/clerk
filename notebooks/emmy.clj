(ns emmy
  (:require [emmy.env :as e]
            [emmy.mechanics.lagrange]))

;; `emmy.env` is importing vars via potemkin. This notebook exists to
;; test that the analysis finds the correct locations for imported
;; vars.

(let [L (emmy.mechanics.lagrange/L-pendulum 'g 'm 'l)]
  (((e/Lagrange-equations L)
    (e/literal-function 'theta_1))
   't))

(comment
  (require '[nextjournal.clerk.analyzer :as analyzer]
           '[clj-async-profiler.core :as prof])

  
  (analyzer/unhashed-deps (:->analysis-info @nextjournal.clerk.webserver/!doc))

  (analyzer/find-location 'emmy.util.stopwatch/repr)
  (analyzer/find-location 'emmy.value/zero?)
  (meta #'emmy.value/zero?)
  
  (meta #'emmy.value/Value)

  (def parsed
    (nextjournal.clerk.parser/parse-file "notebooks/emmy.clj"))

  (def analyzed
    (analyzer/analyze-doc parsed))
  
  (time (do
          (prof/profile (dotimes [i 10] (analyzer/build-graph analyzed)))
          :done))

  )

#_(nextjournal.clerk/clear-cache!)
