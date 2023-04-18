;; # 🪟 Windows
(ns tap-window
  {:nextjournal.clerk/visibility {:code :hide :result :hide}
   :nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.tap :as tap]
            [nextjournal.clerk.window :as window]))

(clerk/window! :my-window (clerk/html [:div.w-8.h-8.bg-green-500]))

(comment
  (clerk/destroy-window! :my-window)
  (clerk/destroy-all-windows!)
  (clerk/window! ::window/taps)
  (tap> (clerk/html [:div.w-8.h-8.bg-green-500]))
  (tap> (clerk/plotly {:data [{:x [1 2 3 4]}]}))
  (tap/reset-taps!))
