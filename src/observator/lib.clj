(ns observator.lib
  (:require [clojure.string :as str]))

(defn fix-case [s]
  (str/lower-case s))
