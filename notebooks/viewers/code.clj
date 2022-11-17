;; # 🧷 Code Viewer
(ns viewers.code
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.render.code :as-alias render.code]))

;; Code as data
(clerk/code '(def fib (lazy-cat [0 1] (map + fib (rest fib)))))
;; Code as string
(clerk/code "(def fib (lazy-cat [0 1] (map + fib (rest fib))))")

;; Editable code viewer
(clerk/with-viewer
  '(fn [code-str _] [:div.viewer-code [render.code/editor code-str]])
  "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")

;; customize extensions
(clerk/with-viewer
  '(fn [code-str _]
     [:div.bg-slate-100
      [render.code/editor code-str {:extensions
                                    (.concat (codemirror.view/lineNumbers)
                                             render.code/default-extensions
                                             render.code/paredit-keymap
                                             (render.code/on-change (fn [text]
                                                                      (js/console.log :text-changed text))))}]])
  "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")
