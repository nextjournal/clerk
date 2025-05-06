;; # 🖼️ Single Image
(ns viewers.single-image
  (:require [nextjournal.clerk :as clerk]))

(clerk/image "trees.png")

#_(nextjournal.clerk/build! {:index "notebooks/viewers/single_image.clj" :browse true})
