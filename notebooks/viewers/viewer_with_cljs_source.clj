(ns viewers.viewer-with-cljs-source
  (:require [nextjournal.clerk :as clerk]))

(clerk/intern-sci-var
 'viewers.viewer-with-cljs-source
 'my-already-defined-function
 '(fn [x]
    [:div
     [:p "This is a custom pre-defined viewer function!"]
     [nextjournal.clerk.render/inspect x]]))

(def my-cool-viewer
  {:render-fn `my-already-defined-function
   :transform-fn (fn [x] x)})
