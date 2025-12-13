;; # Custom Viewers with d3-require
^{:nextjournal.clerk/visibility {:code :hide}}
(ns d3-require
  (:require [nextjournal.clerk :as clerk]))

;; This is a custom viewer for [Mermaid](https://mermaid-js.github.io/mermaid), a markdown-like syntax for creating diagrams from text. Note that this library isn't bundles with Clerk but we use a component based on [d3-require](https://github.com/d3/d3-require) to load it at runtime.
(def mermaid {:transform-fn clerk/mark-presented
              :render-fn '(fn [value]
                            (when value
                              [nextjournal.clerk.render/with-d3-require {:package ["mermaid@11.3.0/dist/mermaid.min.js"]}
                               (fn [_]
                                 [:div {:ref (fn [el]
                                               (when el
                                                 (let [m js/mermaid
                                                       id (str (gensym))]
                                                   (.initialize m (js-obj :startOnLoad false))
                                                   (-> (.render m id value)
                                                       (.then (fn [result]
                                                                (set! (.-innerHTML el) (.-svg result))))
                                                       (.catch (fn [err]
                                                                 (js/console.error "Mermaid render error:" err)))))))}])]))})

;; We can then use  the above viewer using `with-viewer`.
(clerk/with-viewer mermaid
  "stateDiagram-v2
    [*] --> Still
    Still --> [*]

    Still --> Moving
    Moving --> Still
    Moving --> Crash
    Crash --> [*]")
