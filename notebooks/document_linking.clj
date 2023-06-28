;; # 🖇️ Document Linking
(ns document-linking
  {:nextjournal.clerk/toc true}
  (:require [nextjournal.clerk :as clerk]))

;; ## `clerk/doc-url` helper
;; The helper `clerk/doc-url` allows to reference notebooks by path. We currently support relative paths with respect to the directory which started the Clerk application. An optional trailing hash fragment can appended to the path in order for the page to be scrolled up to the indicated identifier.
(clerk/html
 [:ol
  [:li [:a {:href (clerk/doc-url "notebooks/viewers/html")} "HTML"]]
  [:li [:a {:href (clerk/doc-url "notebooks/viewers/image")} "Images"]]
  [:li [:a {:href (clerk/doc-url "notebooks/markdown.md" "appendix")} "Markdown / Appendix"]]
  [:li [:a {:href (clerk/doc-url "notebooks/how_clerk_works" "step-3:-analyzer")} "Clerk Analyzer"]]
  [:li [:a {:href (clerk/doc-url "book")} "The 📕Book"]]
  [:li [:a {:href (clerk/doc-url "")} "Homepage"]]])


;; ## Client Side
;; The same functionality is available in the SCI context when building render functions.
(clerk/with-viewer
  '(fn [_ _]
     [:ol
      [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/viewers/html")} "HTML"]]
      [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/markdown")} "Markdown"]]
      [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/viewer_api")} "Viewer API / Tables"]]]) nil)
