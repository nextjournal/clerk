(ns nextjournal.clerk.walk)

#?(:clj
   (defn editable? [coll]
     (instance? clojure.lang.IEditableCollection coll))
   :cljs
   (defn ^boolean editable? [coll]
     (implements? cljs.core.IEditableCollection coll)))

(defn map-entry [k v]
  #?(:clj  (clojure.lang.MapEntry. k v)
     :cljs (cljs.core.MapEntry. k v nil)))

(defn walk
  "Traverses form, an arbitrary data structure.  inner and outer are
   functions.  Applies inner to each element of form, building up a
   data structure of the same type, then applies outer to the result.
   Recognizes all Clojure data structures. Consumes seqs as with doall.

   This version uses transients and reduce instead of seq where possible and
   tries not to re-create collections if nothing inside changed (identical?)"
  [inner outer form]
  (cond
    (map-entry? form)
    (let [k  (key form)
          v  (val form)
          k' (inner k)
          v' (inner v)]
      (if (and (identical? k' k) (identical? v' v))
        (outer form)
        (outer (map-entry k' v'))))

    (and (map? form) (editable? form))
    (let [!new-keys (volatile! (transient #{}))]
      (->
       (reduce-kv
        (fn [m k v]
          (let [entry  (map-entry k v)
                entry' (inner entry)]
            (if (identical? entry' entry)
              m
              (let [[k' v'] entry']
                (cond
                  (not (identical? k' k))
                  (do
                    (vswap! !new-keys conj! k')
                    (if (contains? @!new-keys k)
                      (-> m (assoc! k' v'))
                      (-> m (dissoc! k) (assoc! k' v'))))

                  (not (identical? v' v))
                  (-> m (assoc! k v'))

                  :else
                  m)))))
        (transient form) form)
       (persistent!)
       (with-meta (meta form))
       (outer)))

    (map? form)
    (let [!new-keys (volatile! (transient #{}))]
      (->
       (reduce-kv
        (fn [m k v]
          (let [entry  (map-entry k v)
                entry' (inner entry)]
            (if (identical? entry' entry)
              m
              (let [[k' v'] entry']
                (cond
                  (not (identical? k' k))
                  (do
                    (vswap! !new-keys conj! k')
                    (if (contains? @!new-keys k)
                      (-> m (assoc k' v'))
                      (-> m (dissoc k) (assoc k' v'))))

                  (not (identical? v' v))
                  (-> m (assoc k v'))

                  :else
                  m)))))
        form form)
       (outer)))

    (and (vector? form) (editable? form))
    (->
     (reduce-kv
      (fn [v idx el]
        (let [el' (inner el)]
          (if (identical? el' el)
            v
            (assoc! v idx el'))))
      (transient form) form)
     (persistent!)
     (with-meta (meta form))
     (outer))

    (vector? form)
    (->
     (reduce-kv
      (fn [v idx el]
        (let [el' (inner el)]
          (if (identical? el' el)
            v
            (assoc v idx el'))))
      form form)
     (outer))

    #?(:clj (instance? clojure.lang.IRecord form)
       :cljs (record? form))
    (outer
     (reduce
      (fn [r entry]
        (let [entry' (inner entry)]
          (if (identical? entry' entry)
            r
            (conj r entry'))))
      form form))

    (seq? form)
    (outer
     (let [res (reduce
                (fn [idx el]
                  (let [el' (inner el)]
                    (if (identical? el' el)
                      (inc idx)
                      (let [[left right] (split-at idx form)]
                        (reduced
                         (concat left [el'] (map inner (next right))))))))
                0 form)]
       (if (number? res)
         form
         (with-meta (doall res) (meta form)))))

    (coll? form)
    (outer
     (let [seq (seq form)
           res (reduce
                (fn [idx el]
                  (let [el' (inner el)]
                    (if (identical? el' el)
                      (inc idx)
                      (reduced
                       (let [[left right] (split-at idx seq)
                             empty        (empty form)]
                         (if (editable? empty)
                           (as-> empty %
                             (transient %)
                             (reduce conj! % left)
                             (conj! % el')
                             (transduce (map inner) conj! % right)
                             (persistent! %)
                             (with-meta % (meta form)))
                           (as-> empty %
                             (reduce conj % left)
                             (conj % el')
                             (transduce (map inner) conj % right))))))))
                0 seq)]
       (if (number? res)
         form
         res)))

    :else
    (outer form)))

(defn postwalk
  "Performs a depth-first, post-order traversal of form.  Calls f on
  each sub-form, uses f's return value in place of the original.
  Recognizes all Clojure data structures. Consumes seqs as with doall."
  [f form]
  (walk (partial postwalk f) f form))

(defn prewalk
  "Like postwalk, but does pre-order traversal."
  [f form]
  (walk (partial prewalk f) identity (f form)))

(defn keywordize-keys
  "Recursively transforms all map keys from strings to keywords."
  [m]
  (postwalk
   (fn [form]
     (if (map-entry? form)
       (let [k (key form)]
         (if (string? k)
           [(keyword k) (val form)]
           form))
       form))
   m))

(defn stringify-keys
  "Recursively transforms all map keys from keywords to strings."
  [m]
  (postwalk
   (fn [form]
     (if (map-entry? form)
       (let [k (key form)]
         (if (keyword? k)
           [(name k) (val form)]
           form))
       form))
   m))

(defn prewalk-replace
  "Recursively transforms form by replacing keys in smap with their
  values.  Like clojure/replace but works on any data structure.  Does
  replacement at the root of the tree first."
  [smap form]
  (prewalk #(smap % %) form))

(defn postwalk-replace
  "Recursively transforms form by replacing keys in smap with their
  values.  Like clojure/replace but works on any data structure.  Does
  replacement at the leaves of the tree first."
  [smap form]
  (postwalk #(smap % %) form))
