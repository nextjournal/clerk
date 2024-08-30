;; # 📑 Require CLJS
(ns require-cljs
  (:require [nextjournal.clerk :as clerk]))

;; Given a viewer with a fully-qualified `:render-fn` and
;; `:require-cljs` set to `true`, Clerk will now automatically load
;; this ClojureScript file (along with it's deps) into Clerk's SCI
;; environment in the browser to make it useable there.

(def motion-div-viewer
  {:render-fn 'render-fns/motion-div
   :require-cljs true
   :transform-fn clerk/mark-presented})

(clerk/with-viewer motion-div-viewer
  {:class "w-[150px] h-[150px] bg-purple-200 border-2 border-purple-600 rounded-xl m-6"
   :animate {:rotate 360}
   :transition {:duration 2
                :repeat 12345
                :repeatType "reverse"}})
