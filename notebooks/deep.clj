(ns ^:nextjournal.clerk/no-cache deep
  (:require [hickory.core :as hick]))

(reduce (fn [acc i] (vector acc)) :fin (range 30 0 -1))

(reduce (fn [acc i] (vector i acc)) (range 30 0 -1))

(reduce (fn [acc i] (vector acc i)) (range 30 0 -1))

(reduce (fn [acc i] (vector i [i (inc i)] acc)) (range 30 0 -1))

(reduce (fn [acc i] (vector i acc (inc i))) :fin (range 30 0 -1))

(reduce (fn [acc i] (vector #{i} i acc (inc i) #{(inc i)})) :fin (range 30 0 -1))

(-> "https://github.com/davidsantiago/hickory"
    slurp
    hick/parse
    hick/as-hickory)
