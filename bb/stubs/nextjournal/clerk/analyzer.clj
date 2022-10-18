(ns nextjournal.clerk.analyzer
  (:require [nextjournal.clerk.config :as config]
            [edamame.core :as edamame]))

(defn valuehash [value] "hash")
(defn hash-codeblock [_ {:keys [hash]}] hash)
(defn ->hash-str [value] "hash-str")
(defn deref? [form] false)
(defn hash [doc] doc)
(defn hash-deref-deps [doc _cell] doc)

(defn build-graph [doc]
   (-> doc
       (assoc :->analysis-info (fn [x] {:form x}))
       ;; TODO: read first form, create sci.lang.Namespace
       (assoc :ns *ns*)
       (update :blocks
               (partial into [] (map (fn [{:as b :keys [type text]}]
                                       (cond-> b
                                         (= :code type)
                                         (assoc :form
                                                (edamame/parse-string text
                                                                      {:all true
                                                                       :readers *data-readers*
                                                                       :read-cond :allow
                                                                       :regex #(list `re-pattern %)
                                                                       :features #{:clj}
                                                                       ;; TODO:
                                                                       #_#_:auto-resolve (auto-resolves (or *ns* (find-ns 'user)))})))))))))

(defn exceeds-bounded-count-limit? [x]
  (reduce (fn [_ xs]
            (try
              (let [limit config/*bounded-count-limit*]
                (if (and (seqable? xs) (<= limit (bounded-count limit xs)))
                  (reduced true)
                  false))
              (catch Exception _e
                (reduced true))))
          false
          (tree-seq seqable? seq x)))
