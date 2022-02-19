(ns d3-require
  (:require [nextjournal.clerk :as clerk]))


(clerk/with-viewer {:fetch-fn (fn [_ x] x)
                    :render-fn '(fn [value]
                                  (v/html
                                   (when value
                                     [d3-require/with {:package ["vega-embed@6.11.1"]}
                                      (fn [vega]
                                        (let [embed (.-embed vega)]
                                          [:div {:style {:overflow-x "auto"}} ;; TODO: figure out how to make options customizable
                                           [:div.vega-lite {:ref #(when % (embed % (clj->js value) (clj->js {:actions false})))}]]))])))}
  {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                 :format {:type "topojson" :feature "counties"}}
   :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                    :key "id" :fields ["rate"]}}]
   :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

#_(clerk/serve! {})
