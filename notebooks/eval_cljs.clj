;; # Eval CLJS
(ns eval-cljs
  (:require [nextjournal.clerk :as clerk]))

;; Let's load some .cljs code from a file!
;; Because we want to re-render this notebook when the .cljs code changes, we use `::clerk/no-cache`:

^{::clerk/no-cache true ::clerk/viewer clerk/hide-result}
(def cljs-code (slurp "notebooks/eval_cljs_fns.cljs"))

;; The cljs code looks like this:

^{::clerk/visibility :hide}
(clerk/code cljs-code)

;; Let's evaluate the CLJS code on the client:
^{::clerk/viewer clerk/hide-result}
(clerk/eval-cljs cljs-code)

;; And now let's use those functions!

(clerk/with-viewer {:render-fn 'eval-cljs-fns/heading}
  "My awesome heading")

(clerk/with-viewer {:render-fn 'eval-cljs-fns/paragraph}
  "My awesome paragraph :)")
