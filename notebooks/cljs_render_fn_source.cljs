(ns cljs-render-fn-source
  (:require [nextjournal.clerk.sci-viewer :as v]))

(defn on-click []
  (v/mount))

(defn bar []
  [:pre "Hello yes no ok!"])

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

(defn dude [] :de)

[ 1 2 3]


