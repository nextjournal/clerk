(ns nextjournal.clerk.fixtures.issue-660-repro
  (:require [nextjournal.clerk.fixtures.macros :as m]))

(defn nonsense []
  (m/emit-nonsense))
