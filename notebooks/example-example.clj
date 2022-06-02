;; # 🔭 Clerk Examples: An Example
(ns example-example
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.examples :refer [example]]))

^{::clerk/visibility :hide}
(example
  (+ 1 2)
  (+ 41 1)
  (-> 42 range shuffle)
  (macroexpand '(example (+ 1 2)))
  (clerk/html [:h1 "👋"])
  (range)
  (javax.imageio.ImageIO/read (java.net.URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"))
  (+ 1 2))
