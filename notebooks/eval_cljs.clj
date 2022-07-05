;; # Eval CLJS
(ns eval-cljs
  (:require [clojure.java.io :as io]
            [nextjournal.clerk :as clerk]))

#_(comment
    (clerk/clear-cache!)
  )

^{::clerk/no-cache true}
(clerk/eval-cljs (slurp (clojure.java.io/resource "eval_cljs_fns.cljs")))

^{::clerk/no-cache true}
(clerk/with-viewer {:render-fn 'eval-cljs-fns/heading}
  "My Super Duper")


