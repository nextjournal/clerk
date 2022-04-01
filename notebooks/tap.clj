^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache nextjournal.clerk.tap
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
(defn fetch-tap [{:as opts :keys [describe-fn path offset trace-fn]} x]
  (when trace-fn (trace-fn {:origin 'fetch-tap :xs x}))
  (let [path' (cond-> path
                (not= :tap (peek path)) (conj :tap))]
    (-> (cond-> (update x :tap describe-fn (assoc opts :!budget (atom 100) :path path') path')
          (-> path count dec pos?) :tap)
        (assoc :path path' :replace-path (conj path offset)))))
#_
(cond (:error xs) (update xs :ex-data describe-fn opts [])
      (seq path) (describe-fn (:rows xs) opts [])
      :else (-> (cond-> (update xs :rows describe-fn (dissoc opts :!budget) [])
                  (pos? offset) :rows)
                (assoc :path [:rows] :replace-path [offset])
                (dissoc :nextjournal/viewers)))

^{::clerk/viewer clerk/hide-result}
(def taps-viewer {:render-fn '(fn [taps opts]
                                (v/html [:div.flex.flex-col
                                         (map (fn [tap] (let [{:keys [tap inst key]} (:nextjournal/value tap)]
                                                         (js/console.log :inst inst (type inst))
                                                         (with-meta [:div
                                                                     [:div.text-xs.monospace.gray-200 (.toLocaleTimeString inst)]
                                                                     [:div [v/inspect tap]]] {:key key})))
                                              taps)]))
                  #_#_
                  :fetch-fn (fn [{:as opts :keys [describe-fn path offset]} x]
                              (prn :fetch-taps x)
                              )
                  
                  :transform-fn (fn [taps]
                                  (mapv (partial clerk/with-viewer {:fetch-fn fetch-tap}) taps))})


^{::clerk/viewer (if (= :latest @!view)
                   (update taps-viewer :transform-fn (fn [orig-fn] (fn [xs] (orig-fn (take 1 xs)))))
                   taps-viewer)}
@!taps


;; ---
#_(clerk/with-viewer taps-viewer
    [{:tap 42}
     {:tap (javax.imageio.ImageIO/read (java.net.URL. "file:/Users/mk/Desktop/CleanShot 2022-03-28 at 15.15.15@2x.png"))}
     {:tap 42}])

;; ---
(defn process-trace [trace]
  (-> trace
      (select-keys [:origin :xs :current-path :path :descend?])
      #_
      (update-in [:fetched :tap :nextjournal/value] type)))

(let [!trace (atom [])]
  (nextjournal.clerk.viewer/describe
   (clerk/with-viewer taps-viewer
     [{:tap (javax.imageio.ImageIO/read (java.net.URL. "file:/Users/mk/Desktop/CleanShot 2022-03-28 at 15.15.15@2x.png"))}])
   {:trace-fn #(swap! !trace conj (process-trace %)) :path [0]})
  (clerk/code @!trace))

;; ---

(let [!trace (atom [])]
  (nextjournal.clerk.viewer/describe
   (clerk/table [[(javax.imageio.ImageIO/read (java.net.URL. "file:/Users/mk/Desktop/CleanShot 2022-03-28 at 15.15.15@2x.png"))]])
   {:trace-fn #(swap! !trace conj (process-trace %)) :path [0 0]})
  (clerk/code @!trace))

;; ---
(clerk/table [[(javax.imageio.ImageIO/read (java.net.URL. "file:/Users/mk/Desktop/CleanShot 2022-03-28 at 15.15.15@2x.png"))]])

#_(reset! !taps ())


^{::clerk/viewer clerk/hide-result}
(defn tapped [x]
  (swap! !taps conj {:tap x :inst (java.time.Instant/now) :key (str (gensym))})
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
  (tap> (range 21))
  (tap> (shuffle (range 100)))
  (tap> (javax.imageio.ImageIO/read (java.net.URL. "file:/Users/mk/Desktop/CleanShot 2022-03-28 at 15.15.15@2x.png")))
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
