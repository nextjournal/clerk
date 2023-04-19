;; # 🖇️ Document Linking
(ns document-linking
  (:require [nextjournal.clerk :as clerk]))

;; With `clerk/doc-url` is possible to reference notebooks by path. We currently support relative paths with respect to the directory which started the Clerk application.
(clerk/html
 [:ol (list [:li [:a {:href (clerk/doc-url "notebooks/viewers/html.clj")} "HTML"]]
            [:li [:a {:href (clerk/doc-url "notebooks/viewers/image.clj")} "Images"]]
            [:li [:a {:href (clerk/doc-url "notebooks/markdown.md#appendix")} "Appendix"]]
            [:li [:a {:href (clerk/doc-url "notebooks/viewer_api.clj#tables")} "Viewer API / Tables"]]
            [:li [:a {:href (clerk/doc-url "notebooks/how_clerk_works.clj#step-4:-evaluation")} "Clerk Evaluation"]])])

;; The same functionality is available in the SCI context when building render functions.
(clerk/with-viewer
  '(fn [_ _]
     [:ol (list [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/viewers/html.clj")} "HTML"]]
                [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/markdown.md")} "Markdown"]]
                [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/viewer_api.clj#tables")} "Viewer API / Tables"]])]) nil)
