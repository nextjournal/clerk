(ns render-alias-migration
  (:require [nextjournal.clerk :as clerk]))

(clerk/with-viewer {:render-fn 'v/html :transform-fn clerk/mark-presented}
  [:h1 "hi"])

(clerk/with-viewer {:transform-fn clerk/mark-presented
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
                                                                       (js/console.error "Mermaid render error:" err)))))))}])]))}
  "stateDiagram-v2
    [*] --> Still
    Still --> [*]

    Still --> Moving
    Moving --> Still
    Moving --> Crash
    Crash --> [*]")

#_(println
   (with-out-str
     (clojure.pprint/pprint '(fn [value]
                               (when value
                                 [render/with-d3-require {:package ["mermaid@8.14/dist/mermaid.js"]}
                                  (fn [mermaid]
                                    [:div {:ref (fn [el] (when el
                                                           (.render mermaid (str (gensym)) value #(set! (.-innerHTML el) %))))}])])))))
