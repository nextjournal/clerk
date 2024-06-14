;; # 🎿Evaluation in SCI context
(ns sci-eval
  {:nextjournal.clerk/eval :sci}
  (:require [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.render.hooks :as hooks]))

(js/console.log "SCI…")

(viewer/html
 (fn [_]
   (let [!state (hooks/use-state 0)]
     [:h1 {:on-click #(swap! !state inc)} (str "Count: " @!state)])))

(viewer/html
 (fn [_]
   (let [confetti (hooks/use-promise (js/import "https://cdn.skypack.dev/canvas-confetti"))]
     [:button.bg-teal-500.hover:bg-teal-700.text-white.font-bold.py-2.px-4.rounded.rounded-full.font-sans
      (if confetti {:on-click #(.default confetti)} {:class "bg-gray-200"}) "Peng 🎉!"])))
