;; # 👔 Code Viewer
(ns viewers.code
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

;; Code in some other language, say Rust:
(clerk/code {::clerk/opts {:language "rust"}}
  "fn calculate_factorial(n: u32, result: &mut u32) {
    if n == 0 {
        *result = 1;
    } else {
        *result = n * calculate_factorial(n - 1, result);
    }
}
fn main() {
    let number = 5;
    let mut result = 0;
    calculate_factorial(number, &mut result);
    println!(\"The factorial of {} is: {}\", number, result);
}")

;; Editable code viewer
(clerk/with-viewer
  '(fn [code-str _] [:div.viewer-code [nextjournal.clerk.render.code/editor (reagent.core/atom code-str)]])
  "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")

;; Tiny code viewer
(clerk/with-viewer
  '(fn [code-str _] [nextjournal.clerk.render.code/editor (reagent.core/atom code-str)])
  "{:search :query}")

(clerk/eval-cljs
 '(require '["@codemirror/view" :as cm-view :refer [keymap lineNumbers highlightActiveLine]]))

;; customize extensions
(clerk/with-viewer
  '(fn [code-str _]
     [:div.bg-neutral-50
      [nextjournal.clerk.render.code/editor (reagent.core/atom code-str)
       {:extensions
        (.concat (lineNumbers)
                 (highlightActiveLine)
                 (.of keymap nextjournal.clojure-mode.keymap/paredit))}]])
  "(def fib
  (lazy-cat [0 1]
            (map + fib (rest fib))))")

(def editor-sync-viewer
  (assoc viewer/render-eval-viewer
         :render-fn
         '(fn [!code _]
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
