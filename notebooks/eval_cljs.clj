;; # Eval CLJS
(ns eval-cljs
  (:require [nextjournal.clerk :as clerk]))

;; Let's load some .cljs code from a file!
;; Because we want to re-render this notebook when the .cljs code changes, we use `::clerk/no-cache`:

^{::clerk/no-cache true ::clerk/viewer '(fn [_] (v/html [:span]))}
(def cljs-code (slurp "notebooks/eval_cljs_fns.cljs"))

;; The cljs code looks like this:

(clerk/with-viewer
  {:render-fn '(fn [code]
                 (v/html [:pre code]))}
  cljs-code)

;; In a future version of clerk, we might be able to dynamically load a CLJs highlighter from a CDN. Stay tuned! Anyway, that's not the point here.
;; Let's evaluate the CLJS code on the client:
(clerk/eval-cljs cljs-code)

;; And now let's use those functions!

(clerk/with-viewer {:render-fn 'eval-cljs-fns/heading}
  "My awesome heading")

(clerk/with-viewer {:render-fn 'eval-cljs-fns/paragraph}
  "My awesome paragraph :)")
