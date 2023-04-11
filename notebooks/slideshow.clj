;; # 🎠 Clerk Slideshow
;; ---
(ns slideshow
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk-slideshow :as clerk-slideshow])
  (:import (javax.imageio ImageIO)
           (java.net URL)))

;; This notebook uses a [slideshow viewer](https://github.com/nextjournal/clerk-slideshow) to override its built-in default notebook viewer.
^{::clerk/visibility {:result :hide}}
(clerk/add-viewers! [clerk-slideshow/viewer])

;; By registering it for the current namespace, it takes over the rendering of the whole notebook page and turns it into a presentation.
;;
;; Use left and right keys to navigate, `ESC` for a summary deck.
;;
;; ---
;; ## 📊 Plotly
^{::clerk/visibility {:code :hide}}
(clerk/plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]})

;; ---
;; ## 📈 Vega Lite
^{::clerk/visibility {:code :hide}}
(clerk/vl {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                         :format {:type "topojson" :feature "counties"}}
           :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                            :key "id" :fields ["rate"]}}]
           :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

;; ---
;; ## 🖼️ An Image
^{::clerk/visibility {:code :hide}}
(ImageIO/read (URL. "https://images.unsplash.com/photo-1532879311112-62b7188d28ce?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8"))

;; ---
;; # 👋🏻 Fin.
