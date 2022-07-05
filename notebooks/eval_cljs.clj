;; # Eval CLJS

(require
 '[clojure.java.io :as io]
 '[nextjournal.clerk :as clerk]
 '[nextjournal.clerk.viewer])

#_(comment
    ^{::clerk/no-cache true}
    (clerk/clear-cache!)
    )

;; Let's load `.cljs` code from the classpath. We use `::clerk/no-cache`
;; metadata to pick up any changes in the `.cljs` code when re-rendering the
;; notebook:
^{::clerk/no-cache true}
(clerk/eval-cljs (slurp (clojure.java.io/resource "eval_cljs_fns.cljs")))

;; We return two vars from the `eval_cljs_fns.cljs` code, just to show that we
;; defined to functions, but you don't need to return anything. The code is
;; evaluated in the SCI context on the client. In the following step, we refer to `eval-cljs-fns/heading` and `eval-cljs-fns/paragraph`:

(clerk/with-viewers (clerk/add-viewers
                     [{:pred number?
                       ;; refer to function in loaded code:
                       :render-fn 'eval-cljs-fns/heading}
                      {:pred string?
                       ;; refer to another function:
                       :render-fn 'eval-cljs-fns/paragraph}])
  [1 "To begin at the beginning:"])

;; Because writing `'eval-cljs-fns/heading` can become tedious, we can create an alias on the client-side:

(clerk/eval-cljs (pr-str '(alias 'fns 'eval-cljs-fns)))

(clerk/with-viewers (clerk/add-viewers
                     [{:pred number?
                       ;; refer to function in loaded code:
                       :render-fn 'fns/heading}
                      {:pred string?
                       ;; refer to another function:
                       :render-fn 'fns/paragraph}])
  [1 "To begin at the beginning:"])

(+ 1 2 3)
