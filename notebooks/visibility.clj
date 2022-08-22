;; # Controlling Visibility 🙈
;; You can control visibility in Clerk by setting the `:nextjournal.clerk/visibility` which takes a map with keys `:code` and `:result` to control the visibility for the code cells and its results. Valid values are:
;; * `:show` (the default)
;; * `:hide` to hide the cells w
;; * `:fold` (currently only valid for the `:code` key.

;; A declaration on the `ns` metadata map lets all code cells in the notebook inherit the value.
(ns visibility
  {:nextjournal.clerk/visibility {:code :fold}}
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]))

;; So a cell will only show the result now while you can uncollapse the code cell.
(+ 39 3)

;; If you want, you can override it. So the following cell is shown:
^{::clerk/visibility {:code :show}} (range 25)

;; While this one is completely hidden, without the ability to uncollapse it.
^{::clerk/visibility {:code :hide}} (shuffle (range 25))

;; When you'd like to hide the result of a cell, set `::clerk/visibility` should contain `{:result :hide}`.
^{::clerk/visibility {:code :show :result :hide}}
(def my-range (range 500))


;; Further work:

;; * support setting and changing the defaults for parts of a doc using `{::clerk/visibility {:code :show}}` top-level forms.
;; * remove the `::clerk/visibility` metadata from the displayed code cells to not distract from the essence.


;; Fin.
