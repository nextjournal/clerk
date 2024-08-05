(ns viewers.viewer-with-cljs-source
  (:require [viewers.viewer-lib :as lib]
            [clojure.string :as str]))

(defn my-already-defined-function2 [x]
  [:div
   [:p "This is a custom pre-defined viewer function! :)"]
   [:div
    [:div "str:" (str/join "," (range 10))]
    [lib/my-already-defined-function x]]])

;;;; Scratch

(comment
  (nextjournal.clerk.render/re-render)
  )

