;; # 🎭 Custom Markdown Viewers
(ns viewers.custom-markdown
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

(clerk/set-viewers! [{:name :nextjournal.markdown/text
                      :fetch-fn v/fetch-all
                      :transform-fn (v/into-markup [:span {:style {:color "#f87171"}}])
                      :render-fn 'v/html}

                     {:name :nextjournal.markdown/ruler
                      :transform-fn (v/into-markup [:hr {:style {:border-color "#fb923c"}}])
                      :fetch-fn v/fetch-all
                      :render-fn 'v/html}])

(+ 39 3)

;; ---

(comment
  (reset! v/!viewers (v/get-all-viewers)))
