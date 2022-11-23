(ns notebooks.viewer-classes
  {:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/class "flex items-center justify-center bg-green-500 h-screen gap-4"}
  (:require [nextjournal.clerk :as clerk]))

^{::clerk/class "border-4 border-red-500 animate-bounce"}
(clerk/html
 [:div.bg-white {:class "w-[100px] h-[100px]"}])

^{::clerk/class "border-4 border-blue-500 animate-bounce"}
(clerk/html
 [:div.bg-white {:class "w-[80px] h-[80px]"}])

^{::clerk/class "border-4 border-yellow-500 animate-spin"}
(clerk/html
 [:div.bg-white {:class "w-[60px] h-[60px]"}])
