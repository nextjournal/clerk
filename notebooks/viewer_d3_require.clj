^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(ns d3-require
  (:require [nextjournal.clerk :as clerk]))

(def mermaid {:transform-fn clerk/mark-presented
              :render-fn '(fn [value]
                            (when value
                              [v/with-d3-require {:package ["mermaid@8.14/dist/mermaid.js"]}
                               (fn [mermaid]
                                 [:div {:ref (fn [el] (when el
                                                       (.render mermaid (str (gensym)) value #(set! (.-innerHTML el) %))))}])]))})

^{::clerk/visibility {:result :show}}
(clerk/with-viewer mermaid
  "stateDiagram-v2
    [*] --> Still
    Still --> [*]

    Still --> Moving
    Moving --> Still
    Moving --> Crash
    Crash --> [*]")
