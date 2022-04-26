;; # 🎠 Slideshow Mode
;;
;; ---
;; This notebook shows how to use [Reveal.js](https://revealjs.com/) in a Clerk viewer in order to turn the whole notebook
;; into a presentation.
;;
;; ---
;; Slides are delimited by markdown rulers i.e. with a leading `---` preceded by a newline. A slide can span several blocks
;; of markdown comments as well as code blocks

(ns ^:nextjournal.clerk/no-cache slideshow
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.view :as clerk.view]
            [nextjournal.clerk.webserver :as clerk.webserver]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]
            [lambdaisland.uri.normalize :as uri.normalize]
            [clojure.string :as str]))


;; The key ingredient to achieve this is to allow to override `:clerk/notebook` viewer
;;
;; ---
;; ## TODO
;; * [x] describe root notebook in `n.c.view/doc->viewer`
;; * [x] use v/fetch-all in notebook viewer
;; * [ ] drop using describe-blocks in favour of a simplified with-viewer approach
;; * [x] move `:clerk/notebook` viewer to overridable named viewer
;; * [x] introduce custom notebook viewer here that performs slideshow transformation
;; * [ ] resuse default transform fn in order to be able to process visibility, code folding etc.
;; * [ ] fix registration
;; * [x] fix `n.c.viewer/inspect-leafs`
;; * [x] fix infinite sequences
;; * [x] fix static app (reveal.js seems to break all pages / use unbundled mode)
;; * [ ] fix static app header wrt toc
;; ---
;; Results should be displayed as usual and cells should obey visibility control

^{::clerk/visibility :hide}
(v/vl {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                     :format {:type "topojson" :feature "counties"}}
       :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                        :key "id" :fields ["rate"]}}]
       :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

;; ---
;; Some machinery to split document fragments:

(defn split-by-ruler [{:keys [content]}] (partition-by (comp #{:ruler} :type) content))
(defn ->slide [fragment] (v/with-viewer :clerk/slide fragment))
(defn doc->slides [{:keys [ns blocks]}]
  (transduce (mapcat (fn [{:as block :keys [type result]}]
                       (case type
                         :markdown [block]
                         :code (let [block (update block :result v/apply-viewer-unwrapping-var-from-def)
                                     {:keys [code? result?]} (v/->display block)]
                                 (cond-> []
                                   code?
                                   (conj (dissoc block :result))
                                   result?
                                   (conj (v/value (v/->result ns result false))))))))
             (fn
               ([] {:slides [] :open-fragment []}) ;; init
               ([{:keys [slides open-fragment]}]   ;; complete
                (conj slides (->slide open-fragment)))
               ([acc {:as block :keys [type doc]}]
                (if (not= :markdown type)
                  (update acc :open-fragment conj block)
                  (loop [[first-fragment & tail] (split-by-ruler doc)
                         {:as acc :keys [open-fragment]} acc]
                    (cond
                      (= :ruler (-> first-fragment first :type))
                      (recur tail (cond-> acc
                                          (seq open-fragment)
                                          (-> (update :slides conj (->slide open-fragment))
                                              (assoc :open-fragment []))))
                      (empty? tail)
                      (update acc :open-fragment into first-fragment)
                      'else
                      (recur tail (-> acc
                                      (update :slides conj (->slide (into open-fragment first-fragment)))
                                      (assoc :open-fragment []))))))))
             blocks))

(def slideshow-prose-classes
  (clojure.string/join
    " "
    ["prose max-w-none"
     "prose-h1:mb-0" "prose-h2:mb-8" "prose-h3:mb-8" "prose-h4:mb-8"
     "prose-h1:text-6xl" "prose-h2:text-5xl" "prose-h3:text-3xl" "prose-h4:text-2xl"]))

(def slideshow-viewers
  [{:name :nextjournal.markdown/doc :transform-fn (v/into-markup [:div.viewer-markdown])}

   ;; blocks
   {:name :nextjournal.markdown/code
    :transform-fn #(v/with-viewer :html
                                [:div.viewer-code
                                 (v/with-viewer :code
                                              (md.transform/->text %))])}

   {:name :clerk/slide
    :fetch-fn v/fetch-all
    :transform-fn (fn [fragment]
                    (v/with-viewer
                      :html
                      [:div.flex.flex-col.justify-center
                       {:style {:min-block-size "100vh"}}
                       (->> fragment
                            (into
                              [:div.text-xl.p-20
                               {:class slideshow-prose-classes}]
                              (map (fn [x]
                                     (cond
                                       ((every-pred map? :type) x) (v/with-md-viewer x)
                                       ((every-pred map? :form) x) (v/with-viewer :clerk/code-block x)
                                       'else (v/with-viewer :clerk/result x))))))]))}
   {:name :clerk/notebook
    :transform-fn doc->slides
    :fetch-fn v/fetch-all
    :render-fn '(fn [slides]
                  (v/html
                    (reagent.core/with-let [!state (reagent.core/atom {:current-slide 0
                                                                      :grid? false
                                                                      :viewport-width js/innerWidth
                                                                      :viewport-height js/innerHeight})
                                            ref-fn (fn [el]
                                                     (when el
                                                       (swap! !state assoc :stage-el el)
                                                       (js/addEventListener
                                                         "resize"
                                                         #(swap! !state assoc
                                                                 :viewport-width js/innerWidth
                                                                 :viewport-height js/innerHeight))
                                                       (js/document.addEventListener
                                                         "keydown"
                                                         (fn [e]
                                                           (case (.-key e)
                                                             "Escape" (swap! !state update :grid? not)
                                                             "ArrowRight" (when-not (:grid? !state)
                                                                            (swap! !state update :current-slide #(min (dec (count slides)) (inc %))))
                                                             "ArrowLeft" (when-not (:grid? !state)
                                                                           (swap! !state update :current-slide #(max 0 (dec %))))
                                                             nil)))))
                                            default-transition {:type :spring
                                                                :duration 0.4
                                                                :bounce 0.1}]
                      (let [{:keys [grid? current-slide viewport-width viewport-height]} @!state]
                        [:div.overflow-hidden.relative.bg-slate-50
                         {:ref ref-fn
                          :id "stage"
                          :style {:width viewport-width :height viewport-height}}
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
                                       :class (str "absolute left-0 top-0 overflow-x-hidden bg-white "
                                                   (if grid? "rounded-lg shadow-lg overflow-y-hidden cursor-pointer ring-1 ring-slate-200 hover:ring hover:ring-blue-500/50 active:ring-blue-500"))
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
                                       slide]]))
                                 slides))]))))}])

;; ---
;; this piece of code is to test slideshow mode in cell result view
;;

(comment
  (v/with-viewers (update slideshow-viewers 1 assoc :pred (every-pred map? :blocks :graph))
    (clerk/eval-file "notebooks/hello.clj")))

;; ---
;; And finally actually set the viewers

(clerk/set-viewers! slideshow-viewers)

;; ---
;; reset back to notebook view
(comment
  (clerk/serve! {})
  (reset! v/!viewers (v/get-all-viewers)))
