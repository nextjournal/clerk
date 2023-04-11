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
                 (clerk/html {::clerk/width :full} [:div.h-20.bg-amber-400])
                 (reduce (fn [acc i] (vector i acc)) :fin (range 200 0 -1))
                 (reduce (fn [acc i] (vector i acc)) :fin (range 200 0 -1))))

;; ## Collapsible Sections
;; Fragments allow to hide (and in future versions of Clerk, probably fold) chunks of prose interspersed with results. That is, by using the usual visibility annotation
;;
;;     ^{::clerk/visibility {:code :hide :result :hide}}
;;
;; all the following section can be hidden.

^{::clerk/visibility {:code :hide}}
(clerk/fragment
 (clerk/md "# Title")
 (clerk/code 123)
 123
 (clerk/md "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus condimentum accumsan lacus id laoreet. Maecenas scelerisque rutrum nunc, eu rutrum libero tincidunt eu. Etiam neque mi, sollicitudin in sodales nec, ornare nec dolor. Vivamus non vestibulum erat. Etiam sodales justo lacus, ac ullamcorper sem dignissim eu. Nunc a vehicula elit. Donec orci odio, bibendum ut imperdiet id, fermentum nec ex. Nunc venenatis est quis arcu elementum, non accumsan erat dictum. Donec vitae felis felis.
## Some Section
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus condimentum accumsan lacus id laoreet. Maecenas scelerisque rutrum nunc, eu rutrum libero tincidunt eu. Etiam neque mi, sollicitudin in sodales nec, ornare nec dolor. Vivamus non vestibulum erat. Etiam sodales justo lacus, ac ullamcorper sem dignissim eu. Nunc a vehicula elit. Donec orci odio, bibendum ut imperdiet id, fermentum nec ex. Nunc venenatis est quis arcu elementum, non accumsan erat dictum. Donec vitae felis felis.")
 (clerk/code '(clerk/plotly {:data [{:y [1 2 3]}]}))
 (clerk/plotly {:data [{:y [1 2 3]}]})
 (clerk/md "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus condimentum accumsan lacus id laoreet. Maecenas scelerisque rutrum nunc, eu rutrum libero tincidunt eu. Etiam neque mi, sollicitudin in sodales nec, ornare nec dolor. Vivamus non vestibulum erat. Etiam sodales justo lacus, ac ullamcorper sem dignissim eu. Nunc a vehicula elit. Donec orci odio, bibendum ut imperdiet id, fermentum nec ex. Nunc venenatis est quis arcu elementum, non accumsan erat dictum. Donec vitae felis felis.

---"))

;; And the above will look like as if produced by:
;; # Title
123
;; Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus condimentum accumsan lacus id laoreet. Maecenas scelerisque rutrum nunc, eu rutrum libero tincidunt eu. Etiam neque mi, sollicitudin in sodales nec, ornare nec dolor. Vivamus non vestibulum erat. Etiam sodales justo lacus, ac ullamcorper sem dignissim eu. Nunc a vehicula elit. Donec orci odio, bibendum ut imperdiet id, fermentum nec ex. Nunc venenatis est quis arcu elementum, non accumsan erat dictum. Donec vitae felis felis.
;; ## Some Section
;; Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus condimentum accumsan lacus id laoreet. Maecenas scelerisque rutrum nunc, eu rutrum libero tincidunt eu. Etiam neque mi, sollicitudin in sodales nec, ornare nec dolor. Vivamus non vestibulum erat. Etiam sodales justo lacus, ac ullamcorper sem dignissim eu. Nunc a vehicula elit. Donec orci odio, bibendum ut imperdiet id, fermentum nec ex. Nunc venenatis est quis arcu elementum, non accumsan erat dictum. Donec vitae felis felis.
(clerk/plotly {:data [{:y [1 2 3]}]})
;; Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus condimentum accumsan lacus id laoreet. Maecenas scelerisque rutrum nunc, eu rutrum libero tincidunt eu. Etiam neque mi, sollicitudin in sodales nec, ornare nec dolor. Vivamus non vestibulum erat. Etiam sodales justo lacus, ac ullamcorper sem dignissim eu. Nunc a vehicula elit. Donec orci odio, bibendum ut imperdiet id, fermentum nec ex. Nunc venenatis est quis arcu elementum, non accumsan erat dictum. Donec vitae felis felis.
;;
;; ---
