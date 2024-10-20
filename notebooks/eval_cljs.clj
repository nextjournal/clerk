;; # 📑 Eval CLJS
(ns eval-cljs
  (:require [nextjournal.clerk :as clerk]))

;; > ⚠️ Please see [require_cljs.clj](/notebooks/require_cljs.clj) for a much easier way to do the same thing.

;; Let's load some .cljs code from a file!
;; Because we want to re-render this notebook when the .cljs code changes, we use `::clerk/no-cache`
;; and set `clerk/code` as the viewer so we can see the code:

^{::clerk/no-cache true ::clerk/viewer clerk/code}
(def cljs-code (slurp "notebooks/render_fns.cljs"))

;; Let's evaluate the CLJS code on the client:
(clerk/eval-cljs-str cljs-code)

;; And now let's use those functions!
(def motion-div-viewer
  {:render-fn 'render-fns/motion-div
   :transform-fn clerk/mark-presented})

(clerk/with-viewer motion-div-viewer
  {:class "w-[150px] h-[150px] bg-purple-200 border-2 border-purple-600 rounded-xl m-6"
   :animate {:rotate 360}
   :transition {:duration 2
                :repeat 12345
                :repeatType "reverse"}})
