;; # ⏱ Profiling
(ns profile
  (:require [clj-async-profiler.core :as prof]
            [clj-async-profiler.ui :as prof.ui]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.parser :as parser]
            [viewers.table :as table]))

;; [Go to profiler UI](http://localhost:8080)
(def parsed
  (parser/parse-file "notebooks/rule_30.clj"))

(def analyzed
  (analyzer/analyze-doc parsed))



(do (time (analyzer/build-graph analyzed)) :done)



(prof/profile (analyzer/build-graph analyzed))

(if-not @prof.ui/current-server
  (prof/serve-ui 8080)
  @prof.ui/current-server)


