;; # 👔 Code Viewer
(ns viewers.code
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

;; Code as data
(clerk/code '(def fib (lazy-cat [0 1] (map + fib (rest fib)))))
;; Code as string
(clerk/code "(def fib (lazy-cat [0 1] (map + fib (rest fib))))")

;; Stings with line-breaks and whitespace
(def s
  "0000
   0000

   1111
   1111")

(def ex
  (identity "1000
2000
3000

4000

5000
6000

7000
8000
9000

10000"))

;; Editable code viewer
(clerk/with-viewer
  '(fn [code-str _] [:div.viewer-code [nextjournal.clerk.render.code/editor (reagent/atom code-str)]])
  "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")

;; Tiny code viewer
(clerk/with-viewer
  '(fn [code-str _] [nextjournal.clerk.render.code/editor (reagent/atom code-str)])
  "{:search :query}")

;; customize extensions
(clerk/with-viewer
  '(fn [code-str _]
     [:div.bg-neutral-50
      [nextjournal.clerk.render.code/editor (reagent/atom code-str)
       {:extensions
        (.concat (codemirror.view/lineNumbers)
                 (codemirror.view/highlightActiveLine)
                 nextjournal.clerk.render.code/paredit-keymap)}]])
  "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")

(def editor-sync-viewer
  {:transform-fn (comp viewer/mark-presented (viewer/update-val (comp viewer/->viewer-eval symbol ::clerk/var-from-def)))
   :var-from-def? true
   :render-fn
   '(fn [code-state _]
      [:div.bg-neutral-50
       [nextjournal.clerk.render.code/editor code-state
        {:on-change (fn [text] (reset! code-state text))
         :extensions (.concat (codemirror.view/lineNumbers)
                              (codemirror.view/highlightActiveLine)
                              nextjournal.clerk.render.code/paredit-keymap)}]])})

^{::clerk/sync true ::clerk/viewer editor-sync-viewer}
(defonce editable-code (atom "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))"))

@editable-code

(comment
  (do
    (reset! editable-code "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")
    (clerk/recompute!)))
