(ns viewers.viewer-with-cljs-source
  (:require [nextjournal.clerk :as clerk]))

(clerk/require-cljs 'viewers.viewer-with-cljs-source)

#_(clerk/intern-sci-var
 'viewers.viewer-with-cljs-source
 'my-already-defined-function
 '(fn [x]
    [:div
     "Inspected value!!!!!!"
     [:div [nextjournal.clerk/inspect x]]]))

#_(clerk/intern-sci-var
 'viewers.viewer-with-cljs-source
 'my-already-defined-function2
 `(fn [x#]
    [:div
     [:p "This is a custom pre-defined viewer function!"]
     [:div
      [my-already-defined-function x#]]]))

(def my-cool-viewer
  {:render-fn `my-already-defined-function2
   :transform-fn (fn [x] x)})
