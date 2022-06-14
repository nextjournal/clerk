(ns scratch-viewer
  (:require [nextjournal.clerk.sci-viewer :as v]))

(defn viewer [x]
  (v/html [(keyword (str "h" x)) (str "Heading! " x)]))
