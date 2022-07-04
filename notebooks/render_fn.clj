(require
 '[clojure.java.io :as io]
 '[nextjournal.clerk :as clerk]
 '[nextjournal.clerk.viewer]
 )

(clerk/clear-cache!)
(clerk/eval-cljs (pr-str '(defonce foo :bar)))

;; use no-cache to pick up any changes in the .cljs code when re-rendering the
;; notebook:
^{::clerk/no-cache true}
;; ;; load .cljs code from the classpath
(clerk/eval-cljs (slurp (clojure.java.io/resource "render_fn.cljs")))

^{::clerk/no-cache true}
(clerk/with-viewers (clerk/add-viewers
                     [{:pred number?
                       ;; refer to function in loaded code:
                       :render-fn 'render-fn/heading}
                      {:pred string?
                       ;; refer to another function:
                       :render-fn 'render-fn/paragraph}])
  [1 "To begin at the beginning:"
   2 "It is Spring, moonless night in the small town, starless and bible-black,"
   3 "the cobblestreets silent and the hunched,"
   4 "courters'-and- rabbits' wood limping invisible"
   5 "down to the sloeblack, slow, black, crowblack, fishingboat-bobbing sea."])
;;;;;;;

