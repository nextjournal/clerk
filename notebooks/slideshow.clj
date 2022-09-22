;; # 🎠 Clerk Slideshow
;; ---
^{:nextjournal.clerk/visibility {:code :hide}}
(ns slideshow
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [clojure.walk :as w]))

;; With a custom viewer and some helper functions, we can show a Clerk notebooks as Slideshow.
;;
;; `slide-viewer` wraps a collection of blocks into markup suitable for rendering a slide.

(def slide-viewer
  {:render-fn '(fn [blocks opts]
                 (v/html
                  [:div.flex.flex-col.justify-center
                   {:style {:min-block-size "100vh"}}
                   (into [:div.text-xl.p-20 {:class ["prose max-w-none prose-h1:mb-0 prose-h2:mb-8 rose-h3:mb-8 prose-h4:mb-8"
                                                     "prose-h1:text-6xl prose-h2:text-5xl prose-h3:text-3xl prose-h4:text-2xl"]}]
                         ;; TODO: fix markup for code blocks to use (v/inspect-children opts)
                         (map (fn [{:as block {:keys [name]} :nextjournal/viewer}]
                                [:div {:class (when (= :code name) "viewer-code")}
                                 [v/inspect-presented block]])) blocks)]))})

;; ---
;; The `doc->slides` helper function takes a Clerk notebook and partitions its blocks into slides by occurrences of markdown rulers.
(defn doc->slides [{:as doc :keys [blocks]}]
  (sequence (comp (mapcat (partial v/with-block-viewer doc))
                  (mapcat #(if (= :markdown (v/->viewer %)) (map v/md (-> % v/->value :content)) [%]))
                  (partition-by (comp #{:ruler} :type v/->value))
                  (remove (comp #{:ruler} :type v/->value first))
                  (map (partial v/with-viewer slide-viewer)))
            blocks))
;; ---
;; Lastly, the `slideshow-viewer` overrides the notebook viewer
(def slideshow-viewer
  {:name :clerk/notebook
   :transform-fn (v/update-val doc->slides)
   :render-fn '(fn [slides]
                 (v/html
                  (reagent/with-let [!state (reagent/atom {:current-slide 0
                                                           :grid? false
                                                           :viewport-width js/innerWidth
                                                           :viewport-height js/innerHeight})
                                     ref-fn (fn [el]
                                              (when el
                                                (swap! !state assoc :stage-el el)
                                                (js/addEventListener "resize"
                                                                     #(swap! !state assoc
                                                                             :viewport-width js/innerWidth
                                                                             :viewport-height js/innerHeight))
                                                (js/document.addEventListener "keydown"
                                                                              (fn [e]
                                                                                (case (.-key e)
                                                                                  "Escape" (swap! !state update :grid? not)
                                                                                  "ArrowRight" (when-not (:grid? !state)
                                                                                                 (swap! !state update :current-slide #(min (dec (count slides)) (inc %))))
                                                                                  "ArrowLeft" (when-not (:grid? !state)
                                                                                                (swap! !state update :current-slide #(max 0 (dec %))))
                                                                                  nil)))))
                                     default-transition {:type :spring :duration 0.4 :bounce 0.1}]
                                    (let [{:keys [grid? current-slide viewport-width viewport-height]} @!state]
                                      [:div.overflow-hidden.relative.bg-slate-50
                                       {:ref ref-fn :id "stage" :style {:width viewport-width :height viewport-height}}
                                       (into [:> (.. framer-motion -motion -div)
                                              {:style {:width (if grid? viewport-width (* (count slides) viewport-width))}
                                               :initial false
                                               :animate {:x (if grid? 0 (* -1 current-slide viewport-width))}
                                               :transition default-transition}]
                                             (map-indexed
                                              (fn [i slide]
                                                (let [width 250
                                                      height 150
                                                      gap 40
                                                      slides-per-row (int (/ viewport-width (+ gap width)))
                                                      col (mod i slides-per-row)
                                                      row (int (/ i slides-per-row))]
                                                  [:> (.. framer-motion -motion -div)
                                                   {:initial false
                                                    :class ["absolute left-0 top-0 overflow-x-hidden bg-white"
                                                            (when grid?
                                                              "rounded-lg shadow-lg overflow-y-hidden cursor-pointer ring-1 ring-slate-200 hover:ring hover:ring-blue-500/50 active:ring-blue-500")]
                                                    :animate {:width (if grid? width viewport-width)
                                                              :height (if grid? height viewport-height)
                                                              :x (if grid? (+ gap (* (+ gap width) col)) (* i viewport-width))
                                                              :y (if grid? (+ gap (* (+ gap height) row)) 0)}
                                                    :transition default-transition
                                                    :on-click #(when grid? (swap! !state assoc :current-slide i :grid? false))}
                                                   [:> (.. framer-motion -motion -div)
                                                    {:style {:width viewport-width
                                                             :height viewport-height
                                                             :transformOrigin "left top"}
                                                     :initial false
                                                     :animate {:scale (if grid? (/ width viewport-width) 1)}
                                                     :transition default-transition}
                                                    [v/inspect-presented slide]]]))
                                              slides))]))))})


(clerk/add-viewers! [slideshow-viewer])

;; ---

;; ## 📊 Plotly
^{::clerk/visibility :hide}
(clerk/plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]})

;; ---

;; ## 📈 Vega Lite
^{::clerk/visibility :hide}
(clerk/vl {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                         :format {:type "topojson" :feature "counties"}}
           :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                            :key "id" :fields ["rate"]}}]
           :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

;; ---
;; # 👋🏻 Fin.
