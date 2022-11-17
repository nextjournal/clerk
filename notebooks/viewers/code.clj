;; # 🧷 Code Viewer
(ns viewers.code
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [viewer.control-lab :as controls]))

;; Code as data
(clerk/code '(def fib (lazy-cat [0 1] (map + fib (rest fib)))))
;; Code as string
(clerk/code "(def fib (lazy-cat [0 1] (map + fib (rest fib))))")

;; Editable code viewer
(clerk/with-viewer
  '(fn [code-str _] [:div.viewer-code [nextjournal.clerk.render.code/editor code-str]])
  "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")

;; customize extensions
(clerk/with-viewer
  '(fn [code-str _]
     [:div.bg-slate-100
      [nextjournal.clerk.render.code/editor code-str
       {:extensions
        (.concat (codemirror.view/lineNumbers)
                 nextjournal.clerk.render.code/default-extensions
                 nextjournal.clerk.render.code/paredit-keymap)}]])
  "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")

^{::clerk/sync true
  ::clerk/viewer
  (assoc controls/viewer-eval-viewer
         :render-fn
         '(fn [code-state _]
            [:div.bg-slate-100
             [nextjournal.clerk.render.code/editor @code-state
              {:extensions (.concat (codemirror.view/lineNumbers)
                                    nextjournal.clerk.render.code/default-extensions
                                    nextjournal.clerk.render.code/paredit-keymap)
               :on-change (fn [text] (swap! code-state (constantly text)))}]]))}

(defonce editable-code-2 (atom "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))"))

@editable-code-2
