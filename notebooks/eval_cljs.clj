;; # Eval CLJS

(require
 '[clojure.java.io :as io]
 '[nextjournal.clerk :as clerk]
 '[nextjournal.clerk.viewer :as viewer])

1
1
2


#_(comment
    ^{::clerk/no-cache true}
    (clerk/clear-cache!)
    )

;; Let's load `.cljs` code from the classpath. We use `::clerk/no-cache`
;; metadata to pick up any changes in the `.cljs` code when re-rendering the
;; notebook:
;; ^{::clerk/no-cache true}
;; (def foo (clerk/eval-cljs (slurp (clojure.java.io/resource "eval_cljs_fns.cljs"))))

;; We return two vars from the `eval_cljs_fns.cljs` code, just to show that we
;; defined to functions, but you don't need to return anything. The code is
;; evaluated in the SCI context on the client. In the following step, we refer to `eval-cljs-fns/heading` and `eval-cljs-fns/paragraph`:

(clerk/add-viewers! [{:pred (partial instance? nextjournal.clerk.viewer.ViewerEval)
                      :transform-fn viewer/mark-presented
                      :render-fn '(fn [x]
                                    (js/console.log "x" (pr-str x))
                                    (v/html [:h1 "hi"]))}])

(defn eval-stuff [code]
  `(let [v (binding [*ns* *ns*]
             (load-string ~code))]
     (js/console.log "code" ~code)
     (js/requestAnimationFrame #(do
                                  (js/console.log "mount!!!!")
                                  (v/mount)))
     v))

(comment

  (eval-stuff "(+ 1 2 3)")

  )


^{::clerk/no-cache true}
(def foo (viewer/->ViewerEval
          (eval-stuff (slurp (clojure.java.io/resource "eval_cljs_fns.cljs")))))

(clerk/with-viewers (clerk/add-viewers
                     [{:pred number?
                       ;; refer to function in loaded code:
                       :render-fn 'eval-cljs-fns/heading}
                      {:pred string?
                       ;; refer to another function:
                       :render-fn 'eval-cljs-fns/paragraph
                       :foo foo}])
  [1 "To begin at the beginning1112321"])

;; Because writing `'eval-cljs-fns/heading` can become tedious, we can create an alias on the client-side:
