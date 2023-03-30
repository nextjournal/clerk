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
              [:a.text-lg {:href (nextjournal.clerk.viewer/doc-url "notebooks/viewers/image.clj")} "images"]
              " notebook."]) nil)

(clerk/html
 [:ol (list [:li [:a {:href (clerk/doc-url "notebooks/viewers/image.clj")} "Images"]]
            [:li [:a {:href (clerk/doc-url "notebooks/markdown.md")} "Markdown"]]
            [:li [:a {:href (clerk/doc-url "notebooks/markdown.md#appendix")} "Appendix"]]
            [:li [:a {:href (clerk/doc-url "notebooks/viewer_api.clj#tables")} "Viewer API / Tables"]]
            [:li [:a {:href (clerk/doc-url "notebooks/how_clerk_works.clj#step-4:-evaluation")} "Clerk Evaluation"]])])

(clerk/with-viewer
  '(fn [_ _]
     [:ol (list [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/viewers/image.clj")} "Images"]]
                [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/markdown.md")} "Markdown"]]
                [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/viewer_api.clj#tables")} "Viewer API / Tables"]])]) nil)
