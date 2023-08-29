;; # 🔧 Debug preserving expansion state
(ns fragment-expand
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.string :as string]
            [nextjournal.clerk :as clerk]
            [clojure.data.json :as json]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.experimental :as cx]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as viewer]))

{::clerk/visibility {:code :hide :result :hide}}
(def form->presented-doc (comp view/doc->viewer eval/eval-string pr-str))

(defn get-presentation-info [form]
  (let [info (atom [])]
    (clojure.walk/postwalk (fn [node]
                             (when (and (map? node) (contains? node :nextjournal/presented))
                               (swap! info conj {:hash (:nextjournal/hash node)
                                                 :blob-id (:nextjournal/blob-id node)
                                                 :id (-> node :nextjournal/presented :nextjournal/render-opts :id)}))
                             node)
                           (form->presented-doc form))
    @info))

(get-presentation-info '(clerk/fragment [(range 30)]))

(get-presentation-info '(clerk/fragment [(range 30) 42]))

(defn with-id [id x] (clerk/with-viewer {} {::clerk/render-opts {:id id}} x))

(get-presentation-info '(clerk/fragment [(with-id "id-1" (range 30))]))

(get-presentation-info '(clerk/fragment [(with-id "id-1" (range 30))
                                      (with-id "id-2" 42)]))

{::clerk/visibility {:result :show}}
(range 30)
1
2

(clerk/fragment
 [(range 30)
  8])

(clerk/fragment
 [(with-id "id" 9)
  (with-id "id1" (range 30))
  (with-id "id3" 11)
  ])
