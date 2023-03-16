;; # 🧩Fragments
(ns fragments
  (:require [nextjournal.clerk :as clerk]))

;; With `clerk/fragment` it's possible to embed a sequence of values into the document as if they were results of individual cells.

(clerk/fragment
 (clerk/table  [[1 2] [3 4]])
 (clerk/image {::clerk/width :full} "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg")
 (clerk/plotly {::clerk/width :full} {:data [{:y [1 3 2]}]}))
