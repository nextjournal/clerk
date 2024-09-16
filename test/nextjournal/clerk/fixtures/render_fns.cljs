(ns nextjournal.clerk.fixtures.render-fns
  (:require [nextjournal.clerk]))

(prn ::identity)

(defn id [x]
  [:pre (nextjournal.clerk/inspect x)])

