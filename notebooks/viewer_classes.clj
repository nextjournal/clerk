;; # 🎨 Custom Viewer CSS Classes
(ns viewer-classes
  {:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/doc-css-class [:flex :flex-col :items-center
                                     :justify-center :bg-slate-200 :dark:bg-slate-900 :py-8 :min-h-screen]}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [babashka.fs :as fs]
            [clojure.string :as str]))

;; Setting css classes globally should happen on the viewers for the
;; notebook and results. This makes sense because it customizes the
;; `:render-fn`.

#_
^{::clerk/visibility {:code :show}}
(clerk/add-viewers!
 [(assoc viewer/notebook-viewer
         :render-opts {:css-class [:flex :flex-col :items-center
                                   :justify-center :bg-slate-200 :dark:bg-slate-900 :py-8 :min-h-screen]})

  (assoc viewer/result-viewer
         :render-opts {:css-class [:border :rounded-lg :shadow-lg :bg-white :p-4 :max-w-2xl :mx-auto]})])
#_
(clerk/reset-viewers! (clerk/get-default-viewers))

;; To support customizing the result css class, we can't really use
;; `:render-opts` because its opts don't apply to the result but to
;; the wrapping `result-viewer`. Should we thus stick to
;; `::clerk/css-class` for this?

;; To make things work consistently, we'd then want to support
;; `::clerk/css-class` on the ns meta again to change it doc-wide.

;; We should rename `::clerk/opts` to `::clerk/render-opts` which
;; communicates the intent much better.

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
