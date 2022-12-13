;; # ⏱ Profiling
(ns profile
  (:require [clj-async-profiler.core :as prof]
            [clj-async-profiler.ui :as prof.ui]
            [nextjournal.clerk.analyzer :as analyzer]
            [viewers.table :as table]))

;; [Go to profiler UI](http://localhost:8080)

(prof/profile (analyzer/exceeds-bounded-count-limit? table/letter->words))

(if-not @prof.ui/current-server
  (prof/serve-ui 8080)
  @prof.ui/current-server)


