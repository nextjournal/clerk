(ns notebooks.viewer-classes
  {:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/class [:justify-center :bg-slate-200 :dark:bg-slate-900 :py-8 :min-h-screen]}
  (:require [nextjournal.clerk :as clerk]
            [clojure.string :as str]))

(clerk/html
 {::clerk/class [:border :rounded-lg :shadow-lg :bg-white :p-4 :max-w-2xl :mx-auto]}
 [:div
  (clerk/vl {:width 700 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                           :format {:type "topojson" :feature "counties"}}
             :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                              :key "id" :fields ["rate"]}}]
             :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})])

^{::clerk/viewer clerk/table
  ::clerk/class [:max-w-2xl :mx-auto :bg-white :p-4 :rounded-lg :shadow-lg :mt-4]}
(def dataset
  (->> (slurp "/usr/share/dict/words")
       str/split-lines
       (group-by (comp keyword str/upper-case str first))
       (into (sorted-map))))

