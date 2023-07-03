;; # Clerk Index 🗂

^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache index
  (:require [nextjournal.clerk :as clerk]))

;; A demo of a custom welcome page for the static build generated from `index.clj`.
(clerk/html
 [:div.viewer-markdown
  [:ul
   [:li [:a.underline {:href (clerk/doc-url "notebooks/rule_30")} "Rule 30"]]
   [:li [:a.underline {:href (clerk/doc-url "notebooks/links")} "Link Design"]]
   [:li [:a.underline {:href (clerk/doc-url "notebooks/markdown")} "Markdown"]]]])
