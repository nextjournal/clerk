;; # 🪟 Windows
(ns tap-window
  {:nextjournal.clerk/visibility {:code :hide :result :hide}
   :nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.tap :as tap]))

{::clerk/visibility {:code :show}}

;; Clerk windows are draggable, resizable and dockable containers that are floating on top of other content. Windows make it easy to show arbitary content, independent of a notebook, while still getting all the benefits of Clerk viewers. This can be nice for debugging. For example you could use it to inspect a data structure in one window and show the same data structure as a graph in a second window.

;; Windows have identity. In order to spawn one, you have to call something like:

(clerk/window! :my-window {:foo (vec (repeat 2 {:baz (range 30) :fooze (range 40)})) :bar (range 20)})

;; This creates a window with a `:my-window` id. The id makes the window addressable and, as such, allows to update its contents from the REPL. For example, you can call …

(clerk/window! :my-window {:title "A debug window"} (zipmap (range 1000) (map #(* % %) (range 1000))))

;; … to replace the contens of `:my-window`. The window itself will not be reinstantiated. The example also shows that `window!` takes an optional second `opts` argument that can be used to give it a custom title.

;; Windows have a dedicated close button but you can also use the id to close it from the REPL, e.g.

(clerk/close-window! :my-window)

;; Finally, there's also special `::clerk/taps` window that doesn't require you to set any content. Instead, it will show you a stream of taps (independant of the notebooks you are working in). So, whenever you `tap>` something, the Taps window will show it when it's open:

(comment
  (clerk/window! ::clerk/taps))

;; Mind that windows live outside notebooks and once you spawn one, it shows until you close it again, even if you reload the page or show a different notebook!

(comment
  (clerk/window! :test {:title "My Super-Duper Window"} (range 100))
  (clerk/window! :test (clerk/html [:div.w-8.h-8.bg-green-500]))
  (clerk/close-window! :test)
  (clerk/close-all-windows!)
  (clerk/window! ::clerk/taps)
  (tap> (clerk/html [:div.w-8.h-8.bg-green-500]))
  (tap> (clerk/vl {:description "A simple bar chart with embedded data."
                   :data {:values [{:a "A" :b 28} {:a "B" :b 55} {:a "C" :b 43}
                                   {:a "D" :b 91} {:a "E" :b 81} {:a "F" :b 53}
                                   {:a "G" :b 19} {:a "H" :b 87} {:a "I" :b 52}]}
                   :mark "bar"
                   :encoding {:x {:field "a" :type "nominal" :axis {:labelAngle 0}}
                              :y {:field "b" :type "quantitative"}}}))
  (tap> 1)
  (tap/reset-taps!)
  (clerk/window! ::clerk/sci-repl))
