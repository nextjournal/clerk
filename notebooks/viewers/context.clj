;; # 🪬 Viewer Context

(ns viewers.context
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

;; A viewer's `:pred` function can perform viewer selection based on a value.

;; It would sometimes be useful to have more context available. Examples of this are:
;;
;; * Selecting a viewer based on the originating form
;; * Selecting a different viewer based on additional context like `:path`
;; * Bring Clerk's handling of out-of-band metadata like `::clerk/visibility` and `::clerk/width` into userspace

;; To make this a backwards-compatible change, we can opt into these
;; richer predicate functions using a map with a key:

(def cljc-viewer
  {:pred {:wrapped (fn [{:keys [form]}]
                     (contains? (meta form) :cljc))}
   :transform-fn (fn [{:keys [form]}]
                   (clerk/eval-cljs form))})

;; We should probably use a namespaced keyword to disambiguate it.

;; Also considered letting the pred function opt in using
;; metadata. Rejected this because it's invisible and doesn't work for
;; e.g. keywords.

^::clerk/no-cache
(clerk/add-viewers! [cljc-viewer])


;; Now what can we use this for? We can now, for example, create a
;; viewer in userspace that evalautes a given form in Clojure and sci,
;; allowing us to use it on both sides.

^:cljc
(defn my-greet-fn [x]
  (str "Greetings from " x))

(my-greet-fn "Clojure")

(clerk/eval-cljs '(my-greet-fn "ClojureScript"))
