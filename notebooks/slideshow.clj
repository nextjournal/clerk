;; # 🎠 Clerk in Slideshow Mode
(ns ^:nextjournal.clerk/no-cache slideshow
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.view :as clerk.view]
            [nextjournal.clerk.webserver :as clerk.webserver]
            [nextjournal.clerk.viewer :as v]))

;; This notebook shows how to use [Reveal.js](https://revealjs.com/) in a Clerk viewer in order to turn the whole notebook
;; into a presentation.

;; Each set of contiguous markdown comment (`;;`) lines correspond to a single slide and so does each code block.
;;
;; We might introduce additional semantics for grouping blocks into single slides.

;; ## TODO
;; * [x] describe root notebook mode
;; * [x] use v/fetch-all in notebook viewer
;; * [ ] drop using describe-blocks in favour of a simplified with-viewer approach
;; * [x] move notebook viewer to overridable named viewer
;; * [ ] introduce custom notebook viewer here that performs slideshow transformation
;; * [ ] resuse default transform fn in order to be able to process visibility, code folding etc.

(def slideshow-viewers
  [{:name :clerk/markdown-block :transform-fn (comp (v/into-markup [:section.viewer-markdown.text-left]) :doc)}
   {:name :clerk/code-block :transform-fn (fn [block] (v/html [:section.viewer-code (v/code (:text block))]))}
   {:name :clerk/notebook
    :fetch-fn v/fetch-all
    :transform-fn (comp (partial map #(v/with-viewer (keyword "clerk" (str (name (:type %)) "-block")) %)) :blocks)
    :render-fn '(fn [slides]
                  (v/with-d3-require
                   {:package "reveal.js@4.3.1"}
                   (fn [Reveal]
                     (reagent/with-let
                      [refn (fn [el] (when el (.initialize (Reveal. el (clj->js {:embedded true})))))]
                      (v/html
                       [:div.reveal {:ref refn :style {:border "1px solid black" :width "100%" :height "800px"}}
                        (into [:div.slides] slides)])))))}])

(v/set-viewers! slideshow-viewers)
