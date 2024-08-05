(ns viewers.viewer-lib
  (:require [nextjournal.clerk :as clerk]))

(defn my-already-defined-function [x]
  [:div
   "Inspected value :)"
   [:div [clerk/inspect x]]])

