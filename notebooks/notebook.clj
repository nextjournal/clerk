(ns notebook
  (:require [nextjournal.clerk :as clerk]))

(def test-viewer
  {:render-fn 'formform-test/render-test
   :require-cljs true
   :transform-fn clerk/mark-presented})

(clerk/with-viewer test-viewer
  nil)
