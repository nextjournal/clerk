;; # Compile viewer functions using cherry
(ns notebooks.cherry
  #_{:nextjournal.clerk/visibility {:code :hide}
     :nextjournal.clerk/auto-expand-results? true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [applied-science.js-interop :as j]))

(comment
  (clerk/clear-cache!))

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [:pre (time (do (dotimes [_ 100000]
                        (js/Math.sin 100))
                      (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))])}
  (+ 1 2 3 5))

;; Better performance:

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [:pre
       (time (do (dotimes [_ 100000]
                   (js/Math.sin 100))
                 (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))])
   :evaluator :cherry}
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

;; Input text and compile on the fly with cherry

(clerk/with-viewer
  {:evaluator :cherry
   :render-fn
   '(fn [value]
      (let [default-value "(defn foo [x] (+ x 10))
(foo 10)"
            !input (reagent.core/atom default-value)
            !compiled (reagent.core/atom (nextjournal.clerk.sci-env/cherry-compile-string @!input))
            click-handler (fn []
                            (reset! !compiled (nextjournal.clerk.sci-env/cherry-compile-string @!input)))]
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

(clerk/eval-cljs-str "(prn :hello)" {:evaluator :cherry})
