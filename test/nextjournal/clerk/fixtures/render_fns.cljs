(ns nextjournal.clerk.fixtures.render-fns
  (:require [nextjournal.clerk.render]))

(prn ::identity)

(defn id [x]
  [:pre (nextjournal.clerk.render/inspect x)])

