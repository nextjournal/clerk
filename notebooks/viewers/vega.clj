;; # Vega Lite 🗺
(ns viewers.vega
  (:require [nextjournal.clerk.viewer :as v]))

(v/vl {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                     :format {:type "topojson" :feature "counties"}}
       :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                        :key "id" :fields ["rate"]}}]
       :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})
