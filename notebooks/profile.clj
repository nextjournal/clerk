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



(time
 (prof/profile (dotimes [_ 10]
                 (analyzer/build-graph analyzed))))



(prof/profile
 (dotimes [_ 10]
   (nextjournal.clerk/show! "notebooks/rule_30.clj")))

(prof/profile (analyzer/build-graph analyzed))

(if-not @prof.ui/current-server
  (prof/serve-ui 8080)
  @prof.ui/current-server)


