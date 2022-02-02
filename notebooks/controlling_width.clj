;; # Controlling Width ↔️
^{:nextjournal.clerk/visibility :hide-ns}
(ns width
  (:require [nextjournal.clerk :as clerk]))

;; By default, most results are rendered at `:prose` width.
(clerk/html [:div.w-100.bg-green-200.pl-2 ":prose (default)"])

;; The viewer functions take an optional first argument to change this to `:wide` or `:full`.
(clerk/html {::clerk/width :wide} [:div.w-100.bg-green-200.pl-2 ":wide"])

(clerk/html {::clerk/width :full} [:div.w-100.bg-green-200.pl-2 ":full"])

;; This works with metadata as well.
^{::clerk/width :wide ::clerk/viewer :html}
[:div.w-100.bg-green-200.pl-2 ":wide"]
