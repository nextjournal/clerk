;; # 🧩Fragments
(ns fragments
  (:require [nextjournal.clerk :as clerk]))

;; With `clerk/fragment` we allow to embed a sequence of values into the document as if they were results of individual cells, nesting is allowed.

(clerk/fragment
 1
 (clerk/table  [[1 2] [3 4]])
 2
 (clerk/plotly {::clerk/width :full} {:data [{:y [1 3 2]}]})
 (clerk/fragment 3
                 (clerk/html {::clerk/width :full} [:hr.h-20.bg-amber-200])
                 (clerk/html {::clerk/width :full} [:hr.h-20.bg-amber-300])
                 (clerk/html {::clerk/width :full} [:hr.h-20.bg-amber-400])
                 4))
