(ns nextjournal.clerk.sci-env
  (:require [cljs.analyzer.api :as ana]))

(defmacro def-cljs-core []
  `(do ~@(keep (fn [[k v]]
                 (when (:fn-var v)
                   `(set! ~(symbol "js" (str "globalThis.clerk.cljs_core." k)) ~k)))
               (ana/ns-publics 'cljs.core))))
