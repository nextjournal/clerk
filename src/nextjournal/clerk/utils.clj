(ns nextjournal.clerk.utils
  (:require [alphabase.base58 :as base58]))

(def bb? (System/getProperty "babashka.version"))

(defmacro if-bb [then else]
  (if bb? then else))

(defmacro if-not-bb-and [conds then else]
  (if bb?
    else
    `(if ~conds
       ~then
       ~else)))

(defmacro when-bb [& body]
  (when bb?
    `(do ~@body)))

(defmacro when-not-bb [& body]
  (when (not bb?)
    `(do ~@body)))

(defn ->base58 [x]
  (base58/encode x))
