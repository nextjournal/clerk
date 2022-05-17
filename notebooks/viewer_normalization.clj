^{:nextjournal.clerk/visibility :hide}
(ns viewer-normalization
  (:require [nextjournal.clerk :as clerk]))

(clerk/with-viewer '(fn [v] (v/html [:span "The answer is " v "."]))
  42)

^{::clerk/viewer '#(v/html [:span "The answer is " % "."])}
(do 42)

^{::clerk/viewer {:render-fn '(fn [v] (v/html [:span "The answer is " v "."]))}}
(do 42)


(clerk/with-viewer {:render-fn '(fn [v] (v/html [:span "The answer is " v "."]))}
  42)

(clerk/with-viewer {:render-fn '(fn [v] (v/html [:span "The answer is " v "."])) :transform-fn (comp inc clerk/->value)}
  41)

^{::clerk/viewer {:render-fn '#(v/html [:span "The answer is " % "."]) :transform-fn (comp inc clerk/->value)}}
(do 41)

(clerk/with-viewers [{:pred (constantly true) :render-fn '(fn [v] (v/html [:span "The answer is " v "."])) :transform-fn (comp inc clerk/->value)}]
  41)

^{::clerk/viewer {:render-fn '#(v/html [:span "The answer is " % "."]) :transform-fn (comp inc clerk/->value)}}
(do 41)


^{::clerk/viewers [{:pred (constantly true) :render-fn '(fn [v] (v/html [:span "The answer is " v "."])) :transform-fn (comp inc clerk/->value)}]}
(do 41)
