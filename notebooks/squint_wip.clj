(ns squint-wip
  {:nextjournal.clerk/cache false}
  (:require [nextjournal.clerk :as clerk]))

#_(clerk/clear-cache!)
#_(clerk/halt!)
#_(clerk/serve! {:port 7777})

;; #### 🧙 Evaluator

;; By default, [SCI](https://github.com/babashka/sci) is used for evaluating `:render-fn` functions in the browser.

;; What follows is an intentionally inefficient but fun way to compute
;; the nth fibonacci number and show how long it took.

(def fib-viewer
  {:render-fn '(fn [n opts]
                 (reagent.core/with-let
                   [fib (fn fib [x]
                          (if (< x 2)
                            1
                            (+ (fib (dec x)) (fib (dec (dec x))))))
                    time-before (js/performance.now)
                    nth-fib (fib n)
                    time-after (js/performance.now)]
                   [:div
                    [:p
                     (let [evaluator (or (-> opts :viewer :render-evaluator)
                                         :sci)]
                       (clojure.string/capitalize (name evaluator)))
                     " computed the " n "th fibonacci number (" nth-fib ")"
                     " in " (js/Math.ceil (- time-after time-before) 2) "ms."]]))})

;; (clerk/with-viewer fib-viewer 25)

;; ;; You can opt into [cherry](https://github.com/squint-cljs/cherry) as an
;; ;; alternative evaluator by setting `{::clerk/render-evaluator :cherry}` via the
;; ;; viewers opts (see [Customizations](#customizations)). The main difference between cherry and SCI
;; ;; for viewer functions is performance. For performance-sensitive code cherry is
;; ;; better suited since it compiles directly to JavaScript code.

;; (clerk/with-viewer fib-viewer {::clerk/render-evaluator :cherry} 25)

(clerk/with-viewer {:render-fn '(fn [n opts]
                                  (let [fib (fn fib [x]
                                              (= 1 2)
                                              (if (= x 10)
                                                :hello
                                                (fib (dec x))))]
                                    (fib 20)))}
  {::clerk/render-evaluator :squint} 25)

#_(clerk/with-viewer fib-viewer {::clerk/render-evaluator :squint} 25)
