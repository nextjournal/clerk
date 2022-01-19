(ns ^:nextjournal.clerk/no-cache test123
  (:require [clojure.string :as str]))

(def a 41)

(def answer (inc a))

(str/includes? "foo" "bar")
