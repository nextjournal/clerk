;; # 🔭 Clerk Examples
^{:nextjournal.clerk/visibility :hide-ns}
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
  (javax.imageio.ImageIO/read (java.net.URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif")))
