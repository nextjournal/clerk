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
            [nextjournal.clerk.viewer :as v]))


;; The key ingredient to achieve this is to allow to override `:clerk/notebook` viewer
;;
;; ---
;; ## TODO
;; * [x] describe root notebook mode
;; * [x] use v/fetch-all in notebook viewer
;; * [ ] drop using describe-blocks in favour of a simplified with-viewer approach
;; * [x] move notebook viewer to overridable named viewer
;; * [ ] introduce custom notebook viewer here that performs slideshow transformation
;; * [ ] resuse default transform fn in order to be able to process visibility, code folding etc.
;; * [ ] fix registration
;; * [ ] fix infinite sequences
;; * [ ] fix static app (reveal.js seems to break all pages / use unbundled mode)
;; ---
;;
;; Some machinery to split document fragments:

(defn split-by-ruler [{:keys [content]}] (partition-by (comp #{:ruler} :type) content))
(defn doc->slides [{:keys [blocks]}]
  (let [->slide (fn [fragment] (v/with-viewer :clerk/slide fragment))]
    (transduce identity
               (fn
                 ([] {:slides [] :open-fragment []})        ;; init
                 ([{:keys [slides open-fragment]}]          ;; finalize
                  (conj slides (->slide open-fragment)))
                 ([acc {:as block :keys [type doc]}]
                  (cond
                    (= :code type)
                    (update acc :open-fragment conj block)
                    (= :markdown type)
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
               blocks)))

;; ---
;; the actual viewers:

(def slideshow-viewers
  [{:name :clerk/slide
    :fetch-fn v/fetch-all
    :transform-fn (fn [fragment]
                    (v/with-viewer :html
                      (into [:section.viewer-markdown.text-left]
                            (map (fn [x]
                                   (cond
                                     (v/wrapped-value? x) x
                                     ((every-pred map? :type) x) (v/with-md-viewer x)
                                     ((every-pred map? :form) x) (v/with-viewer :clerk/code-block x)
                                     'else x)))
                            fragment)))}
   {:name :clerk/notebook
    :transform-fn doc->slides
    :fetch-fn v/fetch-all
    :render-fn '(fn [slides]
                  (v/with-d3-require
                   {:package "reveal.js@4.3.1"}
                   (fn [Reveal]
                     (reagent/with-let
                      [refn (fn [el] (when el (.initialize (Reveal. el (clj->js {:embedded true})))))]
                      (v/html
                       [:div.reveal {:ref refn :style {:border "1px solid black" :width "100%" :height "800px"}}
                        (into [:div.slides] slides)])))))}])

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
  (reset! v/!viewers (v/get-all-viewers)))
