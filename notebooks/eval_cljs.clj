;; # Eval CLJS
(ns eval-cljs
  (:require [clojure.java.io :as io]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))



(clerk/add-viewers! [{:pred (partial instance? nextjournal.clerk.viewer.ViewerEval)
                      :transform-fn viewer/mark-presented
                      :render-fn '(fn [x]
                                    (v/html [v/inspect-paginated @(resolve 'eval-cljs-fns/answer)]))}])

(defn eval-stuff [code]
  (viewer/->ViewerEval
   (list 'binding '[*ns* *ns*]
         (list 'load-string code))))



^{::clerk/no-cache true}
(eval-stuff (slurp (clojure.java.io/resource "eval_cljs_fns.cljs")))


^{::clerk/no-cache true}
(clerk/with-viewer {:render-fn 'eval-cljs-fns/heading}
  "My Super Duper")

;; Because writing `'eval-cljs-fns/heading` can become tedious, we can create an alias on the client-side:

