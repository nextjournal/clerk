(ns sci-async
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]))

(clerk/with-viewer
  {:render-fn '(fn [_]
                 [:div "Hello there"])#_sci-async/my-viewer
   #_#_:require-cljs true}
  nil
  )
