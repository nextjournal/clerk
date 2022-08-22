;; # 🕳 Deep Data
^{:nextjournal.clerk/visibility {:code :hide}}
(ns ^:nextjournal.clerk/no-cache deep
  (:require [clojure.string :as str]
            [hickory.core :as hick]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

(reduce (fn [acc i] (vector acc)) :fin (range 300 0 -1))

(reduce (fn [acc i] (vector acc)) :fin (range 300 0 -1))

(reduce (fn [acc i] (vector i acc)) (range 300 0 -1))

(reduce (fn [acc i] (vector acc i)) (range 300 0 -1))

(reduce (fn [acc i] (vector i [i (inc i)] acc)) (range 300 0 -1))

(reduce (fn [acc i] (vector i acc (inc i))) :fin (range 300 0 -1))

(reduce (fn [acc i] (vector #{i} i acc (inc i) #{(inc i)})) :fin (range 300 0 -1))

(-> "https://github.com/davidsantiago/hickory" slurp hick/parse hick/as-hickory)

(def letters->words
  (->> "/usr/share/dict/words"
       slurp
       str/split-lines
       (group-by (comp keyword str/lower-case first))
       (into (sorted-map))))

(clerk/table {:nextjournal/width :full} letters->words)

(clerk/table (repeat 100 (range 100)))
