(ns viewers.viewer-lib
  #?(:cljs (:require [nextjournal.clerk :as clerk]
                     [foo.bar :as-alias foo])))

#?(:cljs `foo/x)

#?(:cljs (defn my-already-defined-function [x]
          [:div
           "Inspected value :)"
           [:div [clerk/inspect x]]]))
