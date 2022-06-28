(require '[nextjournal.clerk :as clerk]
         '[nextjournal.clerk.viewer :as v])



(def eval-cljs-viewer
  {:pred #(instance? nextjournal.clerk.viewer.ViewerEval %)
   :render-fn '(fn [code]
                 (v/html (binding [*ns* *ns*]
                           (let [result (load-string code)]
                             [v/inspect-paginated result] ))))})

(clerk/add-viewers! [eval-cljs-viewer])

(defn eval-cljs [string-or-resource-or-url]
  (clerk/with-viewer eval-cljs-viewer string-or-resource-or-url))

(eval-cljs (pr-str '(defonce foo :bar)))

^{::clerk/no-cache true}
(eval-cljs (slurp (clojure.java.io/resource "render_fn.cljs")))

^{::clerk/no-cache true}
(clerk/with-viewers (clerk/add-viewers
                     [{:pred number?
                       :render-fn 'render-fn/heading}
                      {:pred string?
                       :render-fn 'render-fn/paragraph}])
  [1 "To begin at the beginning:"
   2 "It is Spring, moonless night in the small town, starless and bible-black,"
   3 "the cobblestreets silent and the hunched,"
   4 "courters'-and- rabbits' wood limping invisible"
   5 "down to the sloeblack, slow, black, crowblack, fishingboat-bobbing sea."])
;;;;;;;

