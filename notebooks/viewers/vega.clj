;; # Vega Lite 🗺
(ns viewers.vega
  (:require [nextjournal.clerk :as clerk]))

;; ## Geoshape example with requesting data
[1 2 3]

(clerk/vl {:width 600 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                         :format {:type "topojson" :feature "counties"}}
           :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                            :key "id" :fields ["rate"]}}]
           :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

;; ## Simple example with inline data
;; You can provide options to vega-lite/embed via the `:embed/opts` key, e.g. to hide the actions menu.

(clerk/vl
 {:data {:values [{"a" "A" "b" 28} {"a" "B" "b" 100} {"a" "C" "b" 43}
                  {"a" "D" "b" 91} {"a" "E" "b" 81} {"a" "F" "b" 53}
                  {"a" "G" "b" 19} {"a" "H" "b" 87} {"a" "I" "b" 52}]}
  :mark "bar"
  :encoding {"x" {"field" "a" "type" "nominal" "axis" {"labelAngle" 0}}
             "y" {"field" "b" "type" "quantitative"}}
  :embed/opts {:actions false}})
