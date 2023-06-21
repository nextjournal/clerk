(ns viewer-classes
  {:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/doc-css-class [:justify-center :bg-slate-200 :dark:bg-slate-900 :py-8 :min-h-screen]}
  (:require [babashka.fs :as fs]
            [nextjournal.clerk :as clerk]
            [clojure.string :as str]))

(clerk/html
 {::clerk/css-class [:border :rounded-lg :shadow-lg :bg-white :p-4 :max-w-2xl :mx-auto]}
 [:div
  (clerk/vl {:width 700 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                           :format {:type "topojson" :feature "counties"}}
             :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                              :key "id" :fields ["rate"]}}]
             :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})])

^{::clerk/viewer clerk/table
  ::clerk/css-class [:max-w-2xl :mx-auto :bg-white :p-4 :rounded-lg :shadow-lg :mt-4]}
(def dataset
  (->> (slurp (if (fs/exists? "/usr/share/dict/words")
                "/usr/share/dict/words"
                "https://gist.githubusercontent.com/wchargin/8927565/raw/d9783627c731268fb2935a731a618aa8e95cf465/words"))
       str/split-lines
       (group-by (comp keyword str/upper-case str first))
       (into (sorted-map))))
