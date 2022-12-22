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

;; strings with whitespace
(do "    this is    a    string

with     quite


                some
   whitespace      ")

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

(clerk/eval-cljs
 '(require '["@codemirror/view" :as cm-view :refer [keymap lineNumbers highlightActiveLine]]))

;; customize extensions
(clerk/with-viewer
  '(fn [code-str _]
     [:div.bg-neutral-50
      [nextjournal.clerk.render.code/editor (reagent/atom code-str)
       {:extensions
        (.concat (lineNumbers)
                 (highlightActiveLine)
                 (.of keymap nextjournal.clojure-mode.keymap/paredit))}]])
  "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")

(def editor-sync-viewer
  (assoc viewer/viewer-eval-viewer :render-fn '(fn [!code _]
                                                 [:div.bg-neutral-50 [nextjournal.clerk.render.code/editor !code]])))

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
