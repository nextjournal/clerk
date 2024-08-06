(ns viewers.viewer-with-cljs-source
  (:require [viewers.viewer-lib :as lib]
            [clojure.string :as str]
            [nextjournal.clerk.render.hooks :as hooks]
            ["react" :as React]))

(defn my-already-defined-function2 [x]
  (let [state (hooks/use-state 0)
        [native-state set-state!] (React/useState 0)]
    [:div
     [:p "This is a custom pre-defined viewer function! :)"]
     [:div
      [:div "str:" (str/join "," (range 10))]
      [lib/my-already-defined-function x]
      [:div
       [:button.bg-sky-600 {:on-click #(swap! state inc)}
        "Click me: " @state]]
      [:div
       [:button.bg-sky-600 {:on-click #(set-state! (inc native-state))}
        "Click me: " native-state]]]]))

;;;; Scratch

(comment
  (nextjournal.clerk.render/re-render)
  )

