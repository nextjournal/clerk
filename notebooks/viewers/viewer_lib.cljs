(ns viewers.viewer-lib)

(defn my-already-defined-function [x]
  [:div
   "Inspected value :)"
   [:div [nextjournal.clerk/inspect x]]] )
