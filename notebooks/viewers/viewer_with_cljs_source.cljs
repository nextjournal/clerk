(ns viewers.viewer-with-cljs-source)

(comment
  (nextjournal.clerk.render/re-render)
  )

(defn my-already-defined-function [x]
  [:div
   "Inspected value >>>>>>>>>>>>>>>"
   [:div [nextjournal.clerk/inspect x]]] )

(defn my-already-defined-function2 [x#]
  [:div
   [:p "This is a custom pre-defined viewer function!"]
   [:div
    [my-already-defined-function x#]]])


