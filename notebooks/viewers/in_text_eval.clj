;; #  📝 _In-Text_ Evaluation
^{:nextjournal.clerk/visibility {:code :hide}}
(ns viewers.in-text-eval
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.experimental :as cx]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown.transform :as markdown.transform]
            [viewers.custom-markdown :as custom-md]))

;; Being able to override markdown viewers allows us to get in-text evaluation for free:

^{::clerk/sync true}
(defonce num★ (atom 20))

(def markdown-eval-viewers
  [{:name :nextjournal.markdown/monospace
    :transform-fn (comp eval read-string markdown.transform/->text v/->value)}
   {:name :nextjournal.markdown/ruler
    :transform-fn (constantly (v/with-viewer `v/html-viewer [:div.text-center (repeat @num★ "★")]))}])

^{::clerk/visibility {:result :hide}}
(def markdown+eval-viewer
  (update v/markdown-viewer :add-viewers v/add-viewers markdown-eval-viewers))

^{::clerk/visibility {:result :hide}}
(clerk/add-viewers! [markdown+eval-viewer]) ;; register viewer globally for ns

;; ---

;; Drag the following slider `(cx/slider {:min 1 :max 44} 'num★)` to control the number of stars (currently **`(deref num★)`**) in our custom horizontal rules.

;; ---
