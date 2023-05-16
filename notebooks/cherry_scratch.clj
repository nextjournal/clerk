;; # Compile viewer functions using cherry
(ns notebooks.cherry-scratch
  {;; :nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/no-cache true
   #_#_:nextjournal.clerk/render-evaluator :sci}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.view :as view]))

#_(clerk/clear-cache!)
#_(clerk/halt!)
#_(clerk/serve! {:port 7777})

(clerk/eval-cljs
 {:nextjournal.clerk/render-evaluator :cherry}
 '(defn emoji-picker
    {:async true}
    []
    (js/await (js/import "https://cdn.skypack.dev/emoji-picker-element"))
    (nextjournal.clerk.viewer/html [:div
                                    [:p "My cool emoji picker:"]
                                    [:emoji-picker]])))

(clerk/with-viewer
  {:evaluator :cherry
   :render-fn '(fn [_]
                 [nextjournal.clerk.render/render-promise
                  (emoji-picker)])}
  nil)
