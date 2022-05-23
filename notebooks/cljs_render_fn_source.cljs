(ns cljs-render-fn-source
  (:require [nextjournal.clerk.sci-viewer :as v]
            [reagent.core :as r]))

(def !state (r/atom 0))

(defn bar []
  [:pre "Hello there, state: " @!state])

(comment
  (v/mount)
  (swap! !state inc)
  )

(defn on-click []
  (v/mount)
  (swap! !state inc))

(defn foo []
  [:<>
   [:button {:on-click
             (fn []
               (js/requestAnimationFrame on-click))} "Click me"]
   [bar]])

(defn render-fn
  [_]
  (v/html [:<> [foo]]))

(comment
  (keys (ns-publics 'nextjournal.clerk.sci-viewer))
  ;; to reload UI:
  (v/mount)
  )
