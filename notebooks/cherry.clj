;; # Compile viewer functions using cherry
(ns notebooks.cherry
  {#_#_:nextjournal.clerk/visibility {:code :hide}
   #_#_:nextjournal.clerk/auto-expand-results? true
   :nextjournal.clerk/render-evaluator :cherry}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

#_(clerk/clear-cache!)
#_(clerk/halt!)
#_(clerk/serve! {:port 7777})

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [:pre (time (do (dotimes [_ 100000]
                        (js/Math.sin 100))
                      (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))])}
  {:nextjournal.clerk/render-evaluator :sci}
  (+ 1 2 3 5))

;; Better performance:

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [:pre
       (time (do (dotimes [_ 100000]
                   (js/Math.sin 100))
                 (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))])}
  {:nextjournal.clerk/render-evaluator :cherry}
  (+ 1 2 3 5))

;; Let's use a render function in the :render-fn next

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [nextjournal.clerk.render/render-code "(+ 1 2 3)"])
   :evaluator :cherry}
  (+ 1 2 3 5))

;; Recursive ...

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [nextjournal.clerk.render/inspect {:a (range 30)}])
   :evaluator :cherry}
  nil)

;; cherry vega viewer!

(def cherry-vega-viewer (assoc viewer/vega-lite-viewer :evaluator :cherry))

(clerk/with-viewer
  cherry-vega-viewer
  {:width 700 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                 :format {:type "topojson" :feature "counties"}}
   :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                    :key "id" :fields ["rate"]}}]
   :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

;; ## Input text and compile on the fly with cherry

(clerk/with-viewer
  {:evaluator :cherry
   :render-fn
   '(fn [value]
      (let [default-value "(defn foo [x] (+ x 10))
(foo 10)"
            !input (reagent.core/atom default-value)
            !compiled (reagent.core/atom (nextjournal.clerk.cherry-env/cherry-compile-string @!input))
            click-handler (fn []
                            (reset! !compiled (nextjournal.clerk.cherry-env/cherry-compile-string @!input)))]
        (fn [value]
          [:div
           [:div.flex
            [:div.viewer-code.flex-auto.w-80.mb-2 [nextjournal.clerk.render.code/editor !input]]
            [:button.flex-none.bg-slate-100.mb-2.pl-2.pr-2
             {:on-click click-handler}
             "Compile!"]]
           [:div.bg-slate-50
            [nextjournal.clerk.render/render-code @!compiled]]
           [nextjournal.clerk.render/inspect
            (try (js/eval @!compiled)
                 (catch :default e e))]])))}
  nil)

;; ## Functions defined with `defn` are part of the global context

;; (for now) and can be called in successive expressions

(clerk/eval-cljs-str {:evaluator :cherry
                      :nextjournal.clerk/render-evaluator :cherry}
                     "(defn foo [x] x)")

(clerk/eval-cljs-str {:evaluator :cherry}
                     "(foo 1)")

;; ## Async/await works cherry

;; Here we dynamically import a module, await its value and then pull out the
;; default function, which we expose as a global function. Because s-expressions
;; serialized to the client currently don't preserve metadata in clerk, and
;; async functions need `^:async`, we use a plain string.


^::clerk/no-cache
(clerk/eval-cljs
 {:evaluator :cherry
  :nextjournal.clerk/render-evaluator :cherry}
 '(defn emoji-picker
    {:async true}
    []
    (js/await (js/import "https://cdn.skypack.dev/emoji-picker-element"))
    (nextjournal.clerk.viewer/html [:div
                                    [:p "My cool emoji picker:"]
                                    [:emoji-picker]])))

;; In the next block we call it:

^::clerk/no-cache
(clerk/with-viewer
  {:evaluator :cherry
   :render-fn '(fn [_]
                 [nextjournal.clerk.render/render-promise
                  (emoji-picker)])}
  nil)

;; ## Macros

^::clerk/no-cache
(clerk/eval-cljs
 {:evaluator :cherry}
 '(defn clicks []
    (reagent.core/with-let [!s (reagent.core/atom 0)]
      [:button {:on-click (fn []
                            (swap! !s inc))}
       "Clicks: " @!s])))

^::clerk/no-cache
(clerk/with-viewer
  {:evaluator :cherry
   :render-fn '(fn [_]
                 [clicks])
   }
  nil)

