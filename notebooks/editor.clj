(ns notebooks.editor
  {:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/doc-css-class [:overflow-hidden :p-0]}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

(clerk/with-viewer
  {:render-fn 'nextjournal.clerk.render.editor/view
   :transform-fn clerk/mark-presented}
  ";; # 👋 Hello CLJS
;; This is `fold`
;;
;; $$(\\beta\\rightarrow\\alpha\\rightarrow\\beta)\\rightarrow\\beta\\rightarrow [\\alpha] \\rightarrow\\beta$$
;;
(defn fold [f i xs]
  (if (seq xs)
    (fold f (f i (first xs)) (rest xs))
    i))

(fold str \"\" (range 10))

;; ## And the usual Clerk's perks
(nextjournal.clerk.viewer/plotly {:data [{:y (shuffle (range 10)) :name \"The Federation\"}
                  {:y (shuffle (range 10)) :name \"The Empire\"}]})
;; tables
(nextjournal.clerk.viewer/table {:a [1 2 3] :b [4 5 6]})
;; html
(nextjournal.clerk.viewer/html [:h1 \"🧨\"])
")
