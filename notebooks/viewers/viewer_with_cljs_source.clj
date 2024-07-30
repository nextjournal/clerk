(ns viewers.viewer-with-cljs-source
  (:require [nextjournal.clerk :as clerk]))

(clerk/require-cljs 'viewers.viewer-with-cljs-source)

(def my-cool-viewer
  {:render-fn `my-already-defined-function2
   :transform-fn (fn [x] x)})
