;; # 🎭 Custom Markdown Viewers
(ns viewers.custom-markdown
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

;; This allows us to define custom inline `(clerk/tex "\\beta")` evaluation like to build inline controls `(slider 0 100)` to interact with.
;;
;; _**FIXME:** inline formulas!!!_

(clerk/set-viewers! [{:name :nextjournal.markdown/text
                      :fetch-fn v/fetch-all
                      :transform-fn (v/into-markup [:span {:style {:color "#f87171"}}])
                      :render-fn 'v/html}

                     {:name :nextjournal.markdown/monospace
                      :transform-fn (comp eval read-string markdown.transform/->text)}

                     {:name :nextjournal.markdown/ruler
                      :transform-fn (v/into-markup [:hr {:style {:border-color "#fb923c"}}])
                      :fetch-fn v/fetch-all
                      :render-fn 'v/html}])

;; ---

(comment
  (reset! v/!viewers (v/get-all-viewers)))
