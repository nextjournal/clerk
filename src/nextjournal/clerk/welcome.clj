(ns nextjournal.clerk.welcome
  {:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

(clerk/html
 [:p "Call " [:span.code "nextjournal.clerk/show!"] " from your REPL"
  (when-let [watch-paths (seq (:paths @@#'clerk/!watcher))]
    (into [:<> " or save a file in "]
          (interpose " or " (map #(vector :span.code %) watch-paths))))
  " to make your notebook appear…"])

(clerk/html
 [:ul
  [:li [:a {:href "https://book.clerk.vision"} "📖 Book of Clerk"]]
  [:li [:a {:href "#" :on-click (viewer/->viewer-eval '#(v/clerk-eval '(nextjournal.clerk/show! 'nextjournal.clerk.tap)))} "🚰 Tap Inspector"]]])

