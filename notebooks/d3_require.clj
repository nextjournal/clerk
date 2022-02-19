(ns d3-require
  (:require [nextjournal.clerk :as clerk]))

(clerk/with-viewer {:fetch-fn (fn [_ x] x)
                    :render-fn '(fn [value]
                                  (v/html
                                   (when value
                                     [d3-require/with {:package ["mermaid@8.14/dist/mermaid.js"]}
                                      (fn [mermaid]
                                        [:div {:ref (fn [el] (when el
                                                               (.render mermaid (random-uuid) value (fn [svg] (set! (.-innerHTML el) svg)))))}])])))}
  "stateDiagram-v2
    [*] --> Still
    Still --> [*]

    Still --> Moving
    Moving --> Still
    Moving --> Crash
    Crash --> [*]")

#_(clerk/serve! {})
