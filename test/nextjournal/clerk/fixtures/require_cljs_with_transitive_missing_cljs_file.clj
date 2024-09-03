(ns nextjournal.clerk.fixtures.require-cljs-with-transitive-missing-cljs-file
  (:require [nextjournal.clerk :as clerk]))

(def viewer
  {:require-cljs true
   :render-fn 'nextjournal.clerk.fixtures.render-fns/foobar
   :transform-fn identity})

(clerk/with-viewer viewer 1)

