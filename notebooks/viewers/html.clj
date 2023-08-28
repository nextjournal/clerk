;; # HTML & Hiccup 🧙‍♀️
(ns viewers.html (:require [nextjournal.clerk :as clerk]))

(clerk/html "<h3>Ohai, HTML! 👋</h3>")

(clerk/html [:h3 "We "
             [:i "strongly"]
             " prefer hiccup, don't we? ✨"])

;; Linking to notebooks

(clerk/html
 [:div
  "Go to "
  [:a.text-lg {:href (clerk/doc-url "notebooks/viewers/image")} "images"]
  " notebook."])

(clerk/with-viewer
  '(fn [_ _] [:div
             "Go to "
             [:a.text-lg {:href (nextjournal.clerk.viewer/doc-url "notebooks/viewers/image")} "images"]
             " notebook."]) nil)

(clerk/html
 [:ol
  [:li [:a {:href (clerk/doc-url "notebooks/document_linking")} "Cross Document Linking"]]
  [:li [:a {:href (clerk/doc-url "notebooks/rule_30")} "Rule 30"]]
  [:li [:a {:href (clerk/doc-url "")} "Back"]]])
