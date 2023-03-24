;; # 🧩Fragments
(ns fragments
  (:require [nextjournal.clerk :as clerk]))

;; With `clerk/fragment` we allow to embed a sequence of values into the document as if they were results of individual cells, nesting is allowed.

(clerk/fragment
 (clerk/table [[1 2] [3 4]])
 (clerk/image "trees.png")
 (clerk/plotly {::clerk/width :full} {:data [{:y [1 3 2]}]})
 (clerk/html {::clerk/width :full} [:div.h-20.bg-amber-200])
 (clerk/fragment (clerk/html {::clerk/width :full} [:div.h-20.bg-amber-300])
                 (clerk/html {::clerk/width :full} [:div.h-20.bg-amber-400])))

;; ## Collapsible Sections
;; Fragments allow to hide (and in future versions of Clerk, probably fold) chunks of prose interspersed with results. By using the usual visibility annotation
;;
;;     ^{::clerk/visibility {:code :hide :result :hide}}
;;
;; all of the following section can be hidden.
(clerk/fragment
 (clerk/md "## A Random Graph")
 (clerk/code '(clerk/plotly {:data [{:y (shuffle (range 20))}]}))
 (clerk/plotly {:data [{:y (shuffle (range 20))}]})
 (clerk/html {::clerk/width :wide} [:div.h-20.bg-amber-100])
 (clerk/md "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus condimentum accumsan lacus id laoreet. Maecenas scelerisque rutrum nunc, eu rutrum libero tincidunt eu. Etiam neque mi, sollicitudin in sodales nec, ornare nec dolor. Vivamus non vestibulum erat. Etiam sodales justo lacus, ac ullamcorper sem dignissim eu. Nunc a vehicula elit. Donec orci odio, bibendum ut imperdiet id, fermentum nec ex. Nunc venenatis est quis arcu elementum, non accumsan erat dictum. Donec vitae felis felis.")
 (clerk/code '(clerk/plotly {:data [{:y (shuffle (range 20))}]}))
 (clerk/plotly {:data [{:y (shuffle (range 20))}]})
 (clerk/md "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus condimentum accumsan lacus id laoreet. Maecenas scelerisque rutrum nunc, eu rutrum libero tincidunt eu. Etiam neque mi, sollicitudin in sodales nec, ornare nec dolor. Vivamus non vestibulum erat. Etiam sodales justo lacus, ac ullamcorper sem dignissim eu. Nunc a vehicula elit. Donec orci odio, bibendum ut imperdiet id, fermentum nec ex. Nunc venenatis est quis arcu elementum, non accumsan erat dictum. Donec vitae felis felis."))
