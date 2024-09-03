(ns nextjournal.clerk.fixtures.require-cljs-with-missing-cljs-file
  (:require [nextjournal.clerk :as clerk]))

(def viewer
  {:require-cljs true
   :render-fn 'not-existing/dude
   :transform-fn identity})

(clerk/with-viewer viewer 1)
