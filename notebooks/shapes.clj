;; # Clerk 🖤 Shapes 🔴 🟦
;;
;; This is [Maria's shapes](https://www.maria.cloud/intro) library in Clerk.
^{:nextjournal.clerk/visibility {:code :hide}}
(ns shapes
  (:require [applied-science.shapes :refer :all]
            [nextjournal.clerk :as clerk])
  (:import (applied_science.shapes Shape)))

(def shapes-viewer
  {:pred #(instance? Shape %)
   :transform-fn (clerk/update-val #(clerk/html (to-hiccup %)))})

^::clerk/no-cache
(clerk/add-viewers! [shapes-viewer])

(colorize :red (circle 25))

(colorize :blue (rectangle 50 80))

(map (comp circle inc) (range 30))

(layer
 (colorize "aqua" (circle 40))
 (position 10 10 (colorize "magenta" (triangle 24)))
 (position 45 10 (colorize "magenta" (triangle 24)))
 (position 40 55 (colorize "white" (circle 10))))


;; ### TODO
;;
;; * [ ] Make circles not break lines
;; * [ ] Enable reuse of viewer
