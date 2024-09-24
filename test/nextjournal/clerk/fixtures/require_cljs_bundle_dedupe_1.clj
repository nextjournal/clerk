(ns nextjournal.clerk.fixtures.require-cljs-bundle-dedupe-1
  (:require [nextjournal.clerk :as clerk]))

(def my-viewer
  {:require-cljs true
   :render-fn 'nextjournal.clerk.fixtures.render-fns/id})

(clerk/with-viewer my-viewer
  [1 2 3])

(comment
  )
