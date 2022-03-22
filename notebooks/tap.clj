;; # 🚰 Tap Inspector
^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache tap
  (:require [nextjournal.clerk :as clerk]
            [clojure.core :as core]))

^{::clerk/viewer {:transform-fn (fn [{::clerk/keys [var-from-def]}]
                                  {:var-name (symbol var-from-def) :value @@var-from-def})
                  :commands []
                  :fetch-fn (fn [_ x] x)
                  :render-fn '(fn [{:keys [var-name value]}]
                                (v/html
                                 (let [choices [:stream :latest]]
                                   [:div.flex.justify-between.items-center
                                    (into [:div.flex.items-center.font-sans.text-xs.mb-3 [:span.text-slate-500.mr-2 "View-as:"]]
                                          (map (fn [choice]
                                                 [:button.px-3.py-1.font-medium.hover:bg-indigo-50.rounded-full.hover:text-indigo-600.transition
                                                  {:class (if (= value choice) "bg-indigo-100 text-indigo-600" "text-slate-500")
                                                   :on-click #(v/clerk-eval `(reset! ~var-name ~choice))}
                                                  choice]) choices))
                                    [:button.text-xs.rounded-full.px-3.py-1.border-2.font-sans.hover:bg-slate-100.cursor-pointer {:on-click #(v/clerk-eval `(reset! !taps ()))} "Clear"]])))}}
(defonce !view (atom :stream))


^{::clerk/viewer clerk/hide-result}
(defonce !taps (atom ()))

^{::clerk/viewer (if (= :latest @!view)
                   {:transform-fn first}
                   {:render-fn '#(v/html (into [:div.flex.flex-col] (v/inspect-children %2) %1))})}
@!taps

^{::clerk/viewer clerk/hide-result}
(do
  (defn tapped [x]
    (swap! !taps conj x))

  (defonce setup
    (add-tap tapped))

  (comment
    (tap> (rand-int 1000))
    (tap> (shuffle (range 100)))
    (tap> (javax.imageio.ImageIO/read (java.net.URL. "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg")))
    (tap> (clerk/vl {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                                   :format {:type "topojson" :feature "counties"}}
                     :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                                      :key "id" :fields ["rate"]}}]
                     :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}}))

    ))

#_(do (reset! !taps ()) (clerk/show! "notebooks/tap.clj"))
