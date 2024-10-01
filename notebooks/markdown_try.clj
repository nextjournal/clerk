;; # ✏️ Nextjournal Markdown Live Demo
(ns try
  {:nextjournal.clerk/visibility {:code :hide}}
  (:require [nextjournal.clerk :as clerk]))

(def markdown-editor
  {:require-cljs true
   :render-fn 'nextjournal.clerk.markdown-editor/markdown-editor
   })

;; _Edit markdown text, see parsed AST and transformed hiccup live. Preview how Clerk renders it._
^{::clerk/width :full
  ::clerk/visibility {:code :fold}}
(clerk/with-viewer markdown-editor
  (Object.))
