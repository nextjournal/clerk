(ns viewers.viewer-with-cljs-source
  (:require [nextjournal.clerk :as clerk]))

(clerk/intern-sci-var
 'viewers.viewer-with-cljs-source
 'my-already-defined-function
 '(fn [x]
    (prn :x x)
    (prn :> (nextjournal.clerk/->value x))
    [nextjournal.clerk.render/inspect x]))

(def my-cool-viewer
  {:render-fn `my-already-defined-function
   :transform-fn (fn [x] x)})
