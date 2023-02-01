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
  [:a.text-lg {:href (clerk/doc-url "notebooks/viewers/image.clj")} "images"]
  " notebook."])

(clerk/with-viewer
 '(fn [_ _] [:div
             "Go to "
             [:a.text-lg {:href (v/doc-url "notebooks/viewers/image.clj")} "images"]
             " notebook."]) nil)

(clerk/html
 [:h1 "Title"
  (list [:div "one"]
        [:div "two"]
        [:div "three"])])
