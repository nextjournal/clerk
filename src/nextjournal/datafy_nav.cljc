(ns nextjournal.datafy-nav
  "This namespace contains the functions that deal with datafy/nav on
  the runtime side. They are evaluated when the runtime starts."
  (:require [clojure.datafy :as df]))

(defn safe-datafy
  "Savely datafies an object. Metadata is reattached to the datafied
  object whenever possible."
  [x]
  (let [dtf (cond-> x
              (not (instance? clojure.lang.IRef x))
              df/datafy)]
    (cond-> dtf
      (instance? clojure.lang.IObj dtf)
      ;; o/w infinite objects get printed via metadata
      (vary-meta (fn [m] (merge (dissoc m :clojure.datafy/class :clojure.datafy/obj)
                                (meta x)))))))

(defn generalized-get [ds index]
  (cond (or (list? ds) (instance? clojure.lang.ISeq ds)) (nth ds index)
        :else (get ds index)))

(defn nav->
  "Given an object and a set of keys, navigates into the substructures via
  the nav protocol. Applies datafy to the object before navigating further
  down. Maps are indexed by keys, vectors, lists and lazy-seq's by indices."
  ([o] (safe-datafy o))
  ([o k & ks]
   (let [d (safe-datafy o)
         next (df/nav d k (generalized-get d k))]
     (apply nav-> next ks))))

;; This is the value store for toplevel data/objects.
(def ^:dynamic browsify-values {})

(defn special-nav-> [node-id ks]
  (apply nav-> (get browsify-values node-id) ks))

(defn save-browisfy-value [node-id o]
  (alter-var-root (var browsify-values) assoc node-id o))

(defn browsify [node o]
  (let [node-id (:id node)]
    (cond (:nav-path node)
          (special-nav-> node-id o)

          node-id
          (do (save-browisfy-value node-id o)
              (safe-datafy o))

          :else
          (safe-datafy o))))
