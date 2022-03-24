;; # 🎭 Custom Markdown Viewers
^{:nextjournal.clerk/visibility :hide-ns}
(ns ^{:nextjournal.clerk/no-cache true} viewers.custom-markdown
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown.transform :as markdown.transform]))

(defn slider [min max]
  (clerk/with-viewer {:fetch-fn v/fetch-all
                      :render-fn (list 'fn '[_]
                                       (list 'v/html
                                             [:input {:type :range
                                                      :min min :max max
                                                      :on-change '(fn [x] (js/console.log (.. x -target -value)))}]))}
    nil))

;; Define custom inline `(clerk/tex "\\beta")` evaluation like to build inline controls `(slider 0 100)` to interact with.
;;
;; _**FIXME:** inline formulas!!!_

(clerk/set-viewers! [{:name :nextjournal.markdown/text
                      :fetch-fn v/fetch-all
                      :transform-fn (v/into-markup [:span {:style {:color "#f87171"}}])
                      :render-fn 'v/html}

                     {:name :nextjournal.markdown/monospace
                      :transform-fn (comp eval read-string markdown.transform/->text)}

                     {:name :nextjournal.markdown/ruler
                      :transform-fn (v/into-markup [:hr {:style {:border "3px solid #fb923c"}}])
                      :fetch-fn v/fetch-all
                      :render-fn 'v/html}])

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
