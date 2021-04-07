(ns observator.lib
  (:require [clojure.string :as str]))

(defn fix-case [s]
  (str/upper-case s))

(defn fix-case-2 [s]
  (str/upper-case s))

(defn fix-case-3 [s]
  (str/lower-case s))
