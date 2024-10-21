(ns viewers.viewer-lib
  #?(:cljs (:require [nextjournal.clerk :as clerk]
                     [foo.bar :as-alias foo]
                     [cljs.core :as c]
                     [clojure.math :as math1]
                     [cljs.math :as math2])))

#?(:cljs `foo/x)

#?(:cljs (defn my-already-defined-function [x]
          [:div
           "Inspected value :)"
           (str (cljs.core/inc (math2/floor (math1/floor 1.2))))
           [:div [clerk/inspect x]]]))
