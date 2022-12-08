;; # 👔 Code Viewer
(ns viewers.code
  {:nextjournal.clerk/toc true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

;; ## Code as data
(clerk/code '(def fib (lazy-cat [0 1] (map + fib (rest fib)))))
;; ## Code as string
(clerk/code "(def fib (lazy-cat [0 1] (map + fib (rest fib))))")

;; ## Clojure Syntax Highlight
;; strings with whitespace
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

(do "    this is    a    string

with     quite


                some
   whitespace      ")

;; ## Editable code viewer
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

;; ### Synced Editor
(def editor-sync-viewer
  (assoc viewer/viewer-eval-viewer
         :render-fn '(fn [!code _] [:div.bg-neutral-100 [nextjournal.clerk.render.code/editor !code]])))

^{::clerk/sync true ::clerk/viewer editor-sync-viewer}
(defonce editable-code (atom "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))"))

@editable-code

^{::clerk/visibility {:code :hide}}
(comment
  @editable-code
  (do
    (reset! editable-code "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")
    (clerk/recompute!)))

;; ## Clerk Meta Annotations
;; keep user stuff
^clojure.lang.PersistentHashMap
^{::clerk/visibility {:result :hide} :please "keep me"}
{:a 'nice-map}
;; remove everything
^{::clerk/visibility {:result :show} ::clerk/no-cache true ::clerk/width :wide}
(clerk/html
 [:h4
  (apply str (repeat 10 "♦︎"))
  [:span.mx-5 (rand-int 1000)]
  (apply str (repeat 10 "♦︎"))])
;; simple truthy meta
^::clerk/no-cache
(def random-thing (rand-int 1000))
;; not addressed
(def ^::clerk/no-cache random-thing-2 (rand-int 1000))

;; comments in-between should be preserved
^{:what/is-this 123 :and 'this ::clerk/no-cache true}
;; wait
'fooalsdkfj

^::clerk/no-cache
;; wait
'fooalsdkfj
