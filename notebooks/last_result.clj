(ns last-result
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

(def viewer
  (update v/notebook-viewer :transform-fn (fn [transform-fn-orig]
                                            (comp transform-fn-orig
                                                  (clerk/update-val (fn [doc] (update doc :blocks (partial take-last 1))))))))

(def donut-chart
  (v/plotly {:data [{:values [27 11 25 8 1 3 25]
                     :labels ["US" "China" "European Union" "Russian Federation" "Brazil" "India" "Rest of World"]
                     :text "CO2"
                     :textposition "inside"
                     :domain {:column 1}
                     :hoverinfo "label+percent+name"
                     :hole 0.4
                     :type "pie"}]
             :layout {:showlegend false
                      :width 300
                      :height 300
                      :annotations [{:font {:size 20} :showarrow false :x 0.5 :y 0.5 :text "CO2"}]}
             :config {:responsive true}}))

^{::clerk/no-cache true}
(clerk/add-viewers! [viewer])

^{::clerk/width :full ::clerk/visibility :hide}
(v/row donut-chart donut-chart donut-chart)
