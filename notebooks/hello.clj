;; # Hello, Clerk 👋

;; Clerk enables a _rich_, local-first notebook experience using standard Clojure namespaces.
(ns hello
  (:require [nextjournal.clerk :as clerk]))

(clerk/html [:h1.bg-amber-600 "Ahoi"])

;; Here's a visualization of unemployment in the US.
(clerk/vl {:width 700 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                         :format {:type "topojson" :feature "counties"}}
           :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                            :key "id" :fields ["rate"]}}]
           :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})
