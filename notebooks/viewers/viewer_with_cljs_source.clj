(ns viewers.viewer-with-cljs-source
  (:require [nextjournal.clerk :as clerk]))

(clerk/require-cljs '[viewers.viewer-with-cljs-source :as cljs-view])

(def my-cool-viewer
  {:render-fn `cljs-view/my-already-defined-function2
   :transform-fn (fn [x] x)})
