(ns viewers.viewer-lib
  #?(:cljs (:require [nextjournal.clerk :as clerk]
                     [foo.bar :as-alias foo]
                     [cljs.core :as c])))

#?(:cljs `foo/x)

#?(:cljs (defn my-already-defined-function [x]
          [:div
           "Inspected value :)"
           (str (cljs.core/inc 1))
           [:div [clerk/inspect x]]]))
