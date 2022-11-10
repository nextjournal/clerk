(ns cards-macro
  (:require [nextjournal.clerk.viewer :as v]))

(defmacro card [& body]
  `(v/with-viewer '(fn [_ _]
                     (let [data# (do ~@body)]
                       (cond
                         (nextjournal.clerk.render/valid-react-element? data#) data#
                         (vector? data#) data#
                         'else
                         [nextjournal.clerk.render/inspect data#]))) {:nextjournal.clerk/width :wide} nil))
