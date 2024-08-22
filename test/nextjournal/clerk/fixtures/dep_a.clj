(ns nextjournal.clerk.fixtures.dep-a
  (:require [nextjournal.clerk.git :as git]))

;; this fn will be stored in analyzer info map with an anonymous id
(defn some-function-with-defs-inside []
  (def inline-def 123)
  (:git/sha (git/read-git-attrs)))
