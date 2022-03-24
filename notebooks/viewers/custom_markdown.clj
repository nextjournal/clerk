;; # 🎭 Custom Markdown Viewers
^{:nextjournal.clerk/visibility :hide-ns}
(ns ^{:nextjournal.clerk/no-cache true} viewers.custom-markdown
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown.transform :as markdown.transform]))

;; This notebook should demostrate how to override markdown nodes and to test how they render by default.

;; ## Custom Inline Eval (is this margin ok 👇?)

(defonce slider-state (atom 42))

(defn slider [min max]
  (clerk/with-viewer
    {:fetch-fn (fn [_ x] x)
     :render-fn `(fn [val]
                   (v/html [:input {:type :range
                                    :min ~min
                                    :max ~max
                                    :value val
                                    :on-change #(v/clerk-eval `(reset! slider-state (Integer/parseInt ~(.. % -target -value))))}]))}
    @slider-state))

;; With overridable markdown nodes we can get inline evaluation for free: `(clerk/tex "\\beta")`. We can build inline controls to interact with.
;;
;; Say you don't like the text color of your notebook: fix it with a slider `(slider 0 256)` and set blue values to `@slider-state`. Unfortunately dragging of the slider isn't smooth at all, because of recomputations.

(clerk/set-viewers! [{:name :nextjournal.markdown/text
                      :transform-fn
                      (v/into-markup
                       [:span {:style {:color (str "rgb(130, 130, " @slider-state ")")}}])}

                     {:name :nextjournal.markdown/monospace
                      :transform-fn (comp eval read-string markdown.transform/->text)}

                     {:name :nextjournal.markdown/ruler
                      :transform-fn (v/into-markup [:hr {:style {:border "3px solid #fb923c"}}])}])

;; # Custom Ruler
;; ---
;; Margins in markdown code blocks are still to be fixed, both in fenced blocks
;; ```clojure
;; (+ 1 2)
;; ```
;; as well as in 2-tab indented
;;
;;    this
;;    is some
;;    code


^{::clerk/visibility :hide}
(v/with-viewer :hide-result
  (comment
    (reset! nextjournal.clerk.webserver/!doc nextjournal.clerk.webserver/help-doc)
    (reset! v/!viewers (v/get-all-viewers))))
