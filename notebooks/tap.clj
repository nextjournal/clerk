^{:nextjournal.clerk/visibility :hide}
(ns nextjournal.clerk.tap
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [clojure.core :as core]))

^{::clerk/viewer {:transform-fn (fn [{::clerk/keys [var-from-def]}]
                                  {:var-name (symbol var-from-def) :value @@var-from-def})
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


^{::clerk/viewer clerk/hide-result}
(defn fetch-tap [{:as opts :keys [describe-fn path offset]} x]
  (-> (cond-> (update x :value describe-fn (assoc opts :!budget (atom 100)) path)
        (pos? offset) :value)
      (assoc :path (conj path :value))))

^{::clerk/viewer clerk/hide-result}
(def taps-viewer {:render-fn '(fn [taps opts]
                                (v/html [:div.flex.flex-col
                                         (map (fn [tap] (let [{:keys [value key]} (:nextjournal/value tap)]
                                                         (with-meta [v/inspect value] {:key key})))
                                              taps)]))
                  :transform-fn (fn [taps]
                                  (mapv (partial clerk/with-viewer {:fetch-fn fetch-tap}) taps))})


^{::clerk/viewer (if (= :latest @!view)
                   (update taps-viewer :transform-fn (fn [orig-fn] (fn [xs] (orig-fn (take 1 xs)))))
                   taps-viewer)}
@!taps

#_(reset! !taps ())


^{::clerk/viewer clerk/hide-result}
(defn tapped [x]
  (swap! !taps conj {:value x :inst (java.time.Instant/now) :key (str (gensym))})
  (binding [*ns* (find-ns 'tap)]
    (clerk/recompute!)))

#_(tapped (rand-int 1000))

#_(reset! @(find-var 'clojure.core/tapset) #{})

^{::clerk/viewer clerk/hide-result}
(defonce setup
  (add-tap tapped))

#_(remove-tap tapped)



^{::clerk/viewer clerk/hide-result}
(comment
  (tap> (rand-int 1000))
  (tap> (shuffle (range 100)))
  (tap> (javax.imageio.ImageIO/read (java.net.URL. "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg")))
  (tap> (clerk/vl {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                                 :format {:type "topojson" :feature "counties"}}
                   :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                                    :key "id" :fields ["rate"]}}]
                   :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}}))
  (tap> (v/plotly {:data [{:z [[1 2 3]
                               [3 2 1]]
                           :type "surface"}]}))
  )
