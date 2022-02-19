;; # Custom Viewers with d3-require
^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns d3-require
  (:require [nextjournal.clerk :as clerk]))

;; This is a custom viewer for [Mermaid](https://mermaid-js.github.io/mermaid), a markdown-like syntax for creating diagrams from text. Note that this library isn't bundles with Clerk but we use a component based on [d3-require](https://github.com/d3/d3-require) to load it at runtime.
(def mermaid {:pred string?
              :fetch-fn (fn [_ x] x)
              :render-fn '(fn [value]
                            (v/html
                             (when value
                               [d3-require/with {:package ["mermaid@8.14/dist/mermaid.js"]}
                                (fn [mermaid]
                                  [:div {:ref (fn [el] (when el
                                                         (.render mermaid (str (gensym)) value #(set! (.-innerHTML el) %))))}])])))})

;; We can then use  the above viewer using `with-viewer`.
(clerk/with-viewer mermaid
  "stateDiagram-v2
    [*] --> Still
    Still --> [*]

    Still --> Moving
    Moving --> Still
    Moving --> Crash
    Crash --> [*]")
