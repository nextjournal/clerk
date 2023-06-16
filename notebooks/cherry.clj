;; # Compile viewer functions using cherry
(ns cherry
  {:nextjournal.clerk/render-evaluator :cherry}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

#_(clerk/clear-cache!)
#_(clerk/halt!)
#_(clerk/serve! {:port 7777})

;; Since we set `:nextjournal.clerk/render-evaluator :cherry` on the ns meta, evaluation happens through cherry by default
;; in all codeblocks below. As a test for checking that Cherry is actually being picked up, we drop some `this-as` expressions
;; in the code, since that will throw an exceptions when evaluated by SCI.

(clerk/with-viewer
  '(fn [value]
     [:pre (time (do (dotimes [_ 100000]
                       (js/Math.sin 100))
                     (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))])
  {:nextjournal.clerk/render-evaluator :sci} nil)

;; Better performance:

(clerk/with-viewer
  '(fn [value]
     [:pre
      (time (do (dotimes [_ 100000]
                  (js/Math.sin 100))
                (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))]) nil)

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [:pre (this-as this value)])}
  (+ 1 2 3 5))

;; Let's use a render function in the :render-fn next

(clerk/with-viewer '(fn [value]
                      [nextjournal.clerk.render/render-code "(+ 1 2 3)"]) nil)

;; Recursive ...

(clerk/with-viewer '(fn [value]
                      (this-as this
                        [nextjournal.clerk.render/inspect {:a (range 30)}])) nil)

;; vega viewer still works

(clerk/vl
  {:width 700 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                 :format {:type "topojson" :feature "counties"}}
   :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                    :key "id" :fields ["rate"]}}]
   :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

;; ## Input text and compile on the fly with cherry

(clerk/with-viewer
  {;; :evaluator :cherry
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
            [:button.flex-none.rounded-md.border.border-slate-200.bg-slate-100.mb-2.pl-2.pr-2.font-sans
             {:on-click click-handler} "Compile!"]]
           [:div.bg-slate-100.p-2.border.border-slate-200
            [nextjournal.clerk.render/render-code @!compiled {:language "js"}]]
           [nextjournal.clerk.render/inspect
            (try (js/eval @!compiled)
                 (catch :default e e))]])))}
  {:nextjournal.clerk/render-evaluator :cherry}
  nil)

;; ## Functions defined with `defn` are part of the global context

;; (for now) and can be called in successive expressions

(clerk/eval-cljs-str "(defn foo [x] (this-as this (inc x)))")

(clerk/eval-cljs-str "(foo 1)")

;; ## Async/await works cherry

;; Here we dynamically import a module, await its value and then pull out the
;; default function, which we expose as a global function. Because s-expressions
;; serialized to the client currently don't preserve metadata in clerk, and
;; async functions need `^:async`, we use a plain string.


(clerk/eval-cljs
 '(defn emoji-picker
    {:async true}
    []
    (js/await (js/import "https://cdn.skypack.dev/emoji-picker-element"))
    (nextjournal.clerk.viewer/html [:div
                                    [:p "My cool emoji picker:"]
                                    [:emoji-picker]])))

;; In the next block we call it:

(clerk/with-viewer
  '(fn [_]
     [nextjournal.clerk.render/render-promise
      (emoji-picker)]) nil)

;; ## Macros

(clerk/eval-cljs
 '(defn clicks []
    (reagent.core/with-let [!s (reagent.core/atom 0)]
      [:button.bg-teal-500.hover:bg-teal-700.text-white.font-bold.py-2.px-4.rounded.rounded-full.font-sans
       {:on-click (fn [] (swap! !s inc))}
       "Clicks: " @!s])))

(clerk/with-viewer '(fn [_] (this-as this [clicks])) nil)

;; ## Evaluator option as form metadata
^{::clerk/visibility {:code :hide :result :hide} ::clerk/no-cache true}
(clerk/add-viewers! [(assoc viewer/code-block-viewer :transform-fn (viewer/update-val :text))])

;; Test reverting the default option via metadata:
^{::clerk/render-evaluator :sci}
(clerk/with-viewer '(fn [_] (load-string "[:h1 \"Ahoi\"]")) nil)
