(ns editor
  {:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/doc-css-class [:overflow-hidden :p-0]}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

(clerk/with-viewer
  {:render-fn 'nextjournal.clerk.render.editor/view
   :transform-fn clerk/mark-presented}
  (slurp "notebooks/rule_30.clj"))
