;; # 🔭 Clerk Examples
^{:nextjournal.clerk/visibility {:code :hide}}
(ns example
  (:require [nextjournal.clerk :as clerk]))

;; Outside of Clerk, the `example` macro evaluates to `nil`, just like `clojure.core/comment`. Try this in your editor!

;; But when used in the context of Clerk, it renders the expressions with thier resulting values.

(clerk/example
  (+ 1 2)
  (+ 41 1)
  (-> 42 range shuffle)
  (macroexpand '(example (+ 1 2)))
  (clerk/html [:h1 "👋"])
  (range)
  (javax.imageio.ImageIO/read (java.net.URL. "https://nextjournal.com/data/QmeyvaR3Q5XSwe14ZS6D5WBQGg1zaBaeG3SeyyuUURE2pq?filename=thermos.gif&content-type=image/gif")))
