(ns errors
  (:require [nextjournal.clerk :as clerk]))

(clerk/with-viewer {:render-fn '(fn [x] boom)}
  42)
