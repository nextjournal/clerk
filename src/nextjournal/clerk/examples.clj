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
  {:transform-fn (fn [{:as wrapped-value :nextjournal/keys [viewers] :keys [path current-path]}]
                   (-> wrapped-value
                       v/mark-preserve-keys
                       (assoc :nextjournal/viewer {:render-fn '(fn [{:keys [form val]} opts]
                                                                 (v/html [:div.flex.flex-wrap
                                                                          {:class "py-[7px]"}
                                                                          [:div [:div.bg-slate-100.px-2.rounded
                                                                                 (v/inspect opts form)]]
                                                                          [:div.flex.mt-1
                                                                           [:div.mx-2.font-sans.text-xs.text-slate-500 {:class "mt-[2px]"} "⇒"]
                                                                           (v/inspect opts val)]]))})
                       (update-in [:nextjournal/value :form] clerk/code)))})

(clerk/with-viewer example-viewer
  {:form '(+ 1 2)
   :val 3})

(clerk/with-viewer example-viewer
  {:form '(clerk/html [:h1 "👋"])
   :val (clerk/html [:h1 "👋"])})


(clerk/with-viewer example-viewer
  {:form '(-> 42 range shuffle)
   :val (-> 42 range shuffle)})

;; Outside of Clerk, the `example` macro evaluates to `nil`, just like `clojure.core/comment`. Try this in your editor!
(defmacro example
  [& body]
  (when nextjournal.clerk.config/*in-clerk*
    `(clerk/with-viewer {:transform-fn (clerk/update-val (fn [ex#]
                                                           (clerk/with-viewer {:render-fn '(fn [examples opts]
                                                                                             (v/html (into [:div.border-l-2.border-slate-300.pl-4
                                                                                                            [:div.uppercase.tracking-wider.text-xs.font-sans.text-slate-500.mt-4.mb-2 "Examples"]]
                                                                                                           (v/inspect-children opts) examples)))}
                                                             
                                                             (mapv (partial clerk/with-viewer example-viewer) ex#))))}
       (mapv (fn [form# val#] {:form form# :val val#}) ~(mapv (fn [x#] `'~x#) body) ~(vec body)))))


;; But when used in the context of Clerk, it renders the examples.

(example
 (+ 1 2)
 (+ 41 1)
 (-> 42 range shuffle)
 (macroexpand '(example (+ 1 2)))
 (clerk/html [:h1 "👋"])
 (javax.imageio.ImageIO/read (java.net.URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"))
 )



;; ## TODO
;; - [x] Show all code & results
;; - [x] Styling pass
;; - [x] Support using viewer api for example results
;; - [ ] Hide code cells for examples
;; - [ ] Combine with docstring ns rendering
