(ns nextjournal.clerk.cherry-env
  (:require [cljs.analyzer.api :as ana]))

(defmacro def-cljs-core []
  `(do ~@(keep (fn [[k v]]
                 (when (:fn-var v)
                   `(goog.object/set (.. js/globalThis -clerk -cljs_core)
                                     (str (munge ~(str k))) ~k)))
               (ana/ns-publics 'cljs.core))))
