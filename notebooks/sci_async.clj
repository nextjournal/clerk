(ns sci-async
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]))

(clerk/with-viewer
  {:render-fn 'sci-async/my-viewer
   :require-cljs true}
  nil
  )
