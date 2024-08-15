(ns nextjournal.clerk.always-array-map
  "A persistent data structure that is based on array-map, but doesn't turn into a hash-map by using assoc etc.
   Prints like a normal Clojure map in the order of insertion.")

(set! *warn-on-reflection* true)

(declare assoc-before)

(defn- assoc-after [aam k v]
  (apply array-map (concat (interleave (keys aam) (vals aam)) [k v])))

(deftype AlwaysArrayMap [^clojure.lang.PersistentArrayMap the-map]
  clojure.lang.ILookup
  (valAt [_ k]
    (get the-map k))

  clojure.lang.Seqable
  (seq [_]
    (seq the-map))

  clojure.lang.IPersistentMap
  (assoc [_ k v]
    (if (< (count the-map) 8)
      (->AlwaysArrayMap (assoc the-map k v))
      (->AlwaysArrayMap (assoc-after the-map k v))))

  (assocEx [_ _k _v]
    (throw (ex-info "Not implemented" {})))

  (without [_ k]
    (->AlwaysArrayMap (dissoc the-map k)))

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

  clojure.lang.IMeta
  (meta [_]
    (meta the-map))

  clojure.lang.IObj
  (withMeta [_ meta]
    (->AlwaysArrayMap (with-meta the-map meta)))

  Object
  (toString [_]
    "<always-array-map>"))

(defn assoc-before [aam k v]
  (->AlwaysArrayMap (apply array-map (list* k v (interleave (keys aam) (vals aam))))))

(defn always-array-map [& kvs]
  (->AlwaysArrayMap (apply array-map kvs)))

(defmethod print-method AlwaysArrayMap
  [v ^java.io.Writer writer]
  (.write writer "{")
  (let [write-kv! (fn [k v]
              (.write writer (pr-str k))
              (.write writer " ")
              (.write writer (pr-str v)))]
    (doseq [[k v] (butlast v)]
      (write-kv! k v)
      (.write writer ", "))
    (let [[k v] (last v)]
      (write-kv! k v)))
  (.write writer "}"))

(comment
  (pr-str (always-array-map 1 2))
  (type (assoc (always-array-map 0 0 1 1 2 2 3 3 4 4 5 5 6 6) :a 1 :b 2 :c 3))
  )
