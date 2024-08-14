(ns nextjournal.clerk.always-array-map
  "A persistent data structure that is based on array-map, but doesn't turn into a hash-map by using assoc etc.
   Prints like a normal Clojure map in the order of insertion.")

(set! *warn-on-reflection* true)

(declare assoc-before assoc-after)

(deftype AlwaysArrayMap [^clojure.lang.PersistentArrayMap the-map]
  clojure.lang.ILookup
  (valAt [_ k]
    (get the-map k))

  clojure.lang.Seqable
  (seq [_]
    (seq the-map))

  clojure.lang.IPersistentMap
  (assoc [_ k v]
    (assoc-after the-map k v))

  (assocEx [_ _k _v]
    (throw (ex-info "Not implemented" {})))

  (without [_ _k]
    (throw (ex-info "Not implemented" {})))

  clojure.lang.Associative
  (containsKey [_ k]
    (contains? the-map k))

  clojure.lang.IPersistentCollection
  (equiv [_ other]
    (= the-map other))
  (count [_]
    (count the-map))

  java.lang.Iterable
  (iterator [_]
    (.iterator the-map))

  Object
  (toString [_]
    "<always-array-map>"))

(defn assoc-before [aam k v]
  (apply array-map (list* k v (interleave (keys aam) (vals aam)))))

(defn assoc-after [aam k v]
  (apply array-map (concat (interleave (keys aam) (vals aam)) [k v])))

(defn always-array-map [& kvs]
  (->AlwaysArrayMap (apply array-map kvs)))

(defmethod print-method AlwaysArrayMap
  [v ^java.io.Writer writer]
  (.write writer "{")
  (doseq [[k v] v]
    (.write writer (pr-str k))
    (.write writer " ")
    (.write writer (pr-str v)))
  (.write writer "}"))

(comment
  (pr-str (always-array-map 1 2))
  )
