;; # Clerk Index 🗂

^{:nextjournal.clerk/visibility :hide
  :nextjournal.clerk/no-cache true}
(ns index
  (:require [nextjournal.clerk :as clerk]))

;; A demo of a custom welcome page for the static build generated from `index.clj`.
(clerk/html
 [:div.viewer-markdown
  [:ul
   [:li [:a.underline {:href (clerk/doc-url "notebooks/rule_30.clj")} "Rule 30"]]
   [:li [:a.underline {:href (clerk/doc-url "notebooks/markdown.md")} "Markdown"]]]])
