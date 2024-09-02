(ns viewers.viewer-lib
  #?(:cljs (:require [nextjournal.clerk :as clerk])))

#?(:cljs (defn my-already-defined-function [x]
          [:div
           "Inspected value :)"
           [:div [clerk/inspect x]]]))
