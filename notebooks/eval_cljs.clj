;; # 📑 Eval CLJS
(ns eval-cljs
  (:require [nextjournal.clerk :as clerk]))

;; Let's load some .cljs code from a file!
;; Because we want to re-render this notebook when the .cljs code changes, we use `::clerk/no-cache`
;; and set `clerk/code` as the viewer so we can see the code:

^{::clerk/no-cache true ::clerk/viewer clerk/code}
(def cljs-code (slurp "notebooks/render_fns.cljs"))

;; Let's evaluate the CLJS code on the client:
(clerk/eval-cljs-str cljs-code)

;; And now let's use those functions!

(clerk/with-viewer {:render-fn 'render-fns/heading}
  "My awesome heading")

(clerk/with-viewer {:render-fn 'render-fns/paragraph}
  "My awesome paragraph :)")
