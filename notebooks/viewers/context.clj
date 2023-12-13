;; # 🪬 Viewer Context

(ns viewers.context
  "Or how viewer API functions learn to take advantage of the document context"
  {:nextjournal.clerk/visibility {:code :hide}}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

{::clerk/visibility {:code :show}}
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

;; Now what can we use this for? We can now, for example, create a
;; viewer in userspace that evalautes a given form in Clojure and sci,
;; allowing us to use it on both sides.

^:cljc
(defn my-greet-fn [x]
  (str "Greetings from " x))

(my-greet-fn "Clojure")

(clerk/eval-cljs '(my-greet-fn "ClojureScript"))

;; Or build a viewer which will display the namespace's name and docs:

(def ns-viewer
  {:pred {:wrapped :ns?}
   :transform-fn (fn [{:as wv :keys [form]}]
                   (let [doc-ns (find-ns (second form))]
                     (clerk/html [:blockquote.bg-sky-50.rounded.py-1
                                  (clerk/md (str "## " (ns-name doc-ns) "\n" (:doc (meta doc-ns))))])))})

;; In addition, by customizing the cell viewer we might override visibility defaults
(def cell-viewer
  (update viewer/cell-viewer
          :transform-fn comp
          (clerk/update-val (fn [cell]
                              (update-in cell [:settings ::clerk/visibility :result]
                                         #(if (or (:ns? cell)
                                                  (-> cell :result :nextjournal/value (as-> n (when (number? n) (even? n)))))
                                            :show
                                            %))))))

;; given by e.g. a visibility marker.
;;
;;    {::clerk/visibility {:result :hide}}
;;
;; The number `2` should still be displayed here:

{::clerk/visibility {:result :hide :code :hide}}

1
2
3

^::clerk/no-cache
(clerk/add-viewers! [cljc-viewer ns-viewer cell-viewer])
