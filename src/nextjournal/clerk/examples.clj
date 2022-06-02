;; # 🔭 Clerk Examples
^{:nextjournal.clerk/visibility :fold-ns}
(ns ^:nextjournal.clerk/no-cache nextjournal.clerk.examples
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [clojure.string :as str]
            [rewrite-clj.parser :as p]
            [clojure.java.io :as io]
            [multihash.core :as multihash]
            [multihash.digest :as digest]))

;; The example viewer shows the form and it's resulting value.
(def example-viewer
  {:transform-fn (fn [wrapped-value]
                   (-> wrapped-value
                       clerk/mark-presented
                       (assoc :nextjournal/viewer {:render-fn 'v/html})
                       (update :nextjournal/value (fn [{:keys [form val]}]
                                                    [:div.flex.flex-wrap
                                                     {:class "py-[7px]"}
                                                     [:div [:div.bg-slate-100.px-2.rounded (v/inspect-wrapped-value (clerk/code form))]]
                                                     [:div.flex.mt-1
                                                      [:div.mx-2.font-sans.text-xs.text-slate-500 {:class "mt-[2px]"} "⇒"]
                                                      (v/inspect-wrapped-value (v/present val))]]))))})

(clerk/with-viewer example-viewer
  {:form '(+ 1 2)
   :val 3})

(clerk/with-viewer example-viewer
  {:form '(clerk/html [:h1 "👋"])
   :val (clerk/html [:h1 "👋"])})

(clerk/with-viewer example-viewer
  {:form '(vec (range 10))
   :val [0 1 2 3 4 5 6 7 8 9]})

(v/inspect-wrapped-value [0 1 2 3 4 5 6 7 8 9])

;; Outside of Clerk, the `example` macro evaluates to `nil`, just like `clojure.core/comment`. Try this in your editor!
(defmacro example
  [& body]
  (when nextjournal.clerk.config/*in-clerk*
    `(clerk/with-viewer {:transform-fn (clerk/update-val (fn [ex#]
                                                           (clerk/html (into [:div.border-l-2.border-slate-300.pl-4
                                                                              [:div.uppercase.tracking-wider.text-xs.font-sans.text-slate-500.mt-4.mb-2 "Examples"]]
                                                                             (mapv (partial clerk/with-viewer example-viewer) ex#)))))}
       (mapv (fn [form# val#] {:form form# :val val#}) ~(mapv (fn [x#] `'~x#) body) ~(vec body)))))


;; But when used in the context of Clerk, it renders the examples.

(example
 (+ 1 2)
 (+ 41 1)
 (-> 42 range shuffle)
 (macroexpand '(example (+ 1 2)))
 (clerk/html [:h1 "👋"])
 )



;; ## TODO
;; - [x] Show all code & results
;; - [x] Styling pass
;; - [ ] Support using viewer api for example results
;; - [ ] Hide code cells for examples
;; - [ ] Combine with docstring ns rendering
