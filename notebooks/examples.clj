;; # 🔭 Clerk Examples
^{:nextjournal.clerk/visibility :fold-ns}
(ns ^:nextjournal.clerk/no-cache nextjournal.clerk.examples
  (:require [nextjournal.clerk :as clerk]
            [clojure.string :as str]
            [rewrite-clj.parser :as p]
            [clojure.java.io :as io]
            [multihash.core :as multihash]
            [multihash.digest :as digest]))

;; The example viewer shows the form and it's resulting value.
(def example-viewer
  {:transform-fn (fn [{:keys [form val]}]
                   (clerk/html [:div (clerk/code (str form " ⇒ " (pr-str val))) ]))})

(clerk/with-viewer example-viewer
  {:form '(+ 1 2)
   :val 3})

;; Outside of Clerk, the `example` macro evaluates to `nil`, just like `clojure.core/comment`. Try this in your editor!
(defmacro example
  [& body]
  (when nextjournal.clerk.config/*in-clerk*
    `(clerk/with-viewer {:transform-fn (fn [ex#]
                                         (clerk/html (into [:div.flex.flex-col
                                                            [:h5 "Examples"]]
                                                           (mapv (partial clerk/with-viewer example-viewer) ex#))))}
       (mapv (fn [form# val#] {:form form# :val val#}) ~(mapv (fn [x#] `'~x#) body) ~(vec body)))))


;; But when used in the context of Clerk, it renders the examples.

(example
 (macroexpand '(example (+ 1 2)))
 (+ 1 2))

;; ## TODO
;; - [x] Show all code & results
;; - [ ] Styling pass
;; - [ ] Support using viewer api for example results
;; - [ ] Hide code cells for examples
;; - [ ] Combine with docstring ns rendering
