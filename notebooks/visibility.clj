;; # Controlling Visibility 🙈
;; You can control visibility in Clerk by setting the `:nextjournal.clerk/visibility`. Here, we set this to `#{:hide-ns :fold}` to hide the namespace declaration and fold all other code cells.
^{:nextjournal.clerk/visibility #{:hide-ns :fold}}
(ns visibility
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]))

;; So a cell will only show the result now while you can uncoallpse the code cell.
(+ 39 3)

;; If you want, you can override it. So the following cell is shown:
^{::clerk/visibility :show} (range 25)

;; While this one is completely hidden, without the ability to uncollapse it.
^{::clerk/visibility :hide} (shuffle (range 25))

;; Fin.
