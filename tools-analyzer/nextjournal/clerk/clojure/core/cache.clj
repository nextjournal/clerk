;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:doc "A caching library for Clojure."
      :author "Fogus"}
  nextjournal.clerk.clojure.core.cache
  (:require [nextjournal.clerk.clojure.data.priority-map :as clojure.data.priority-map])
  (:import (java.lang.ref ReferenceQueue SoftReference)
           (java.util.concurrent ConcurrentHashMap)))

(set! *warn-on-reflection* true)

;; # Protocols and Types

(defprotocol CacheProtocol
  "This is the protocol describing the basic cache capability."
  (lookup [cache e]
          [cache e not-found]
   "Retrieve the value associated with `e` if it exists, else `nil` in
   the 2-arg case.  Retrieve the value associated with `e` if it exists,
   else `not-found` in the 3-arg case.")
  (has?    [cache e]
   "Checks if the cache contains a value associated with `e`")
  (hit     [cache e]
   "Is meant to be called if the cache is determined to contain a value
   associated with `e`")
  (miss    [cache e ret]
   "Is meant to be called if the cache is determined to **not** contain a
   value associated with `e`")
  (evict  [cache e]
   "Removes an entry from the cache")
  (seed    [cache base]
   "Is used to signal that the cache should be created with a seed.
   The contract is that said cache should return an instance of its
   own type."))

(def ^{:private true} default-wrapper-fn #(%1 %2))

(defn through
  "The basic hit/miss logic for the cache system.  Expects a wrap function and
  value function.  The wrap function takes the value function and the item in question
  and is expected to run the value function with the item whenever a cache
  miss occurs.  The intent is to hide any cache-specific cells from leaking
  into the cache logic itelf."
  ([cache item] (through default-wrapper-fn identity cache item))
  ([value-fn cache item] (through default-wrapper-fn value-fn cache item))
  ([wrap-fn value-fn cache item]
   (if (nextjournal.clerk.clojure.core.cache/has? cache item)
     (nextjournal.clerk.clojure.core.cache/hit cache item)
     (nextjournal.clerk.clojure.core.cache/miss cache item (wrap-fn #(value-fn %) item)))))

(defn through-cache
  "The basic hit/miss logic for the cache system.  Like through but always has
  the cache argument in the first position for easier use with swap! etc."
  ([cache item] (through-cache cache item default-wrapper-fn identity))
  ([cache item value-fn] (through-cache cache item default-wrapper-fn value-fn))
  ([cache item wrap-fn value-fn]
   (if (nextjournal.clerk.clojure.core.cache/has? cache item)
     (nextjournal.clerk.clojure.core.cache/hit cache item)
     (nextjournal.clerk.clojure.core.cache/miss cache item (wrap-fn #(value-fn %) item)))))

(defmacro defcache
  [type-name fields & specifics]
  (let [[base & _] fields
        base-field (with-meta base {:tag 'clojure.lang.IPersistentMap})]
    `(deftype ~type-name [~@fields]
       ~@specifics

       clojure.lang.ILookup
       (valAt [this# key#]
              (lookup this# key#))
       (valAt [this# key# not-found#]
              (if (has? this# key#)
                (lookup this# key#)
                not-found#))

       java.lang.Iterable
       (iterator [_#]
         (.iterator ~base-field))

       clojure.lang.IPersistentMap
       (assoc [this# k# v#]
         (miss this# k# v#))
       (without [this# k#]
         (evict this# k#))

       clojure.lang.Associative
       (containsKey [this# k#]
         (has? this# k#))
       (entryAt [this# k#]
         (when (has? this# k#)
           (clojure.lang.MapEntry. k# (lookup this# k#))))

       clojure.lang.Counted
       (count [this#]
         (count ~base-field))

       clojure.lang.IPersistentCollection
       (cons [this# elem#]
         (seed this# (conj ~base-field elem#)))
       (empty [this#]
         (seed this# (empty ~base-field)))
       (equiv [this# other#]
         (= other# ~base-field))

       clojure.lang.Seqable
       (seq [_#]
         (seq ~base-field)))))

(defcache BasicCache [cache]
  CacheProtocol
  (lookup [_ item]
    (get cache item))
  (lookup [_ item not-found]
    (get cache item not-found))
  (has? [_ item]
    (contains? cache item))
  (hit [this item] this)
  (miss [_ item result]
    (BasicCache. (assoc cache item result)))
  (evict [_ key]
    (BasicCache. (dissoc cache key)))
  (seed [_ base]
    (BasicCache. base))
  Object
  (toString [_] (str cache)))

;; FnCache

(defcache FnCache [cache f]
  CacheProtocol
  (lookup [_ item]
    (f (get cache item)))
  (lookup [_ item not-found]
    (let [ret (get cache item not-found)]
      (if (= not-found ret)
        not-found
        (f ret))))
  (has? [_ item]
    (contains? cache item))
  (hit [this item] this)
  (miss [_ item result]
    (BasicCache. (assoc cache item result)))
  (evict [_ key]
    (BasicCache. (dissoc cache key)))
  (seed [_ base]
    (BasicCache. base))
  Object
  (toString [_] (str cache)))

;; # FIFO

(defn- describe-layout [mappy limit]
  (let [ks (keys mappy)
        [dropping keeping] (split-at (- (count ks) limit) ks)]
    {:dropping dropping
     :keeping  keeping
     :queue
     (-> clojure.lang.PersistentQueue/EMPTY
         (into (repeat (- limit (count keeping)) ::free))
         (into (take limit keeping)))}))

(defn- prune-queue [q k]
  (reduce (fn [q e] (if (#{k} e) q (conj q e)))
          (conj clojure.lang.PersistentQueue/EMPTY ::free)
          q))

(defcache FIFOCache [cache q limit]
  CacheProtocol
  (lookup [_ item]
    (get cache item))
  (lookup [_ item not-found]
    (get cache item not-found))
  (has? [_ item]
    (contains? cache item))
  (hit [this item]
    this)
  (miss [_ item result]
    (let [[kache qq] (let [k (peek q)]
                       (if (>= (count cache) limit)
                         [(dissoc cache k) (pop q)]
                         [cache (pop q)]))]
      (FIFOCache. (assoc kache item result)
                  (conj qq item)
                  limit)))
  (evict [this key]
    (if (contains? cache key)
      (FIFOCache. (dissoc cache key)
                  (prune-queue q key)
                  limit)
      this))
  (seed [_ base]
    (let [{dropping :dropping
           q :queue} (describe-layout base limit)]
      (FIFOCache. (apply dissoc base dropping)
                  q
                  limit)))
  Object
  (toString [_]
    (str cache \, \space (pr-str q))))

(defn- build-leastness-queue
  [base start-at]
  (into (clojure.data.priority-map/priority-map) (for [[k _] base] [k start-at])))

(defcache LRUCache [cache lru tick limit]
  CacheProtocol
  (lookup [_ item]
    (get cache item))
  (lookup [_ item not-found]
    (get cache item not-found))
  (has? [_ item]
    (contains? cache item))
  (hit [_ item]
    (let [tick+ (inc tick)]
      (LRUCache. cache
                 (if (contains? cache item)
                   (assoc lru item tick+)
                   lru)
                 tick+
                 limit)))
  (miss [_ item result]
    (let [tick+ (inc tick)]
      (if (>= (count lru) limit)
        (let [k (if (contains? lru item)
                  item
                  (first (peek lru))) ;; minimum-key, maybe evict case
              c (-> cache (dissoc k) (assoc item result))
              l (-> lru (dissoc k) (assoc item tick+))]
          (LRUCache. c l tick+ limit))
        (LRUCache. (assoc cache item result)  ;; no change case
                   (assoc lru item tick+)
                   tick+
                   limit))))
  (evict [this key]
    (if (contains? cache key)
      (LRUCache. (dissoc cache key)
                 (dissoc lru key)
                 (inc tick)
                 limit)
      this))
  (seed [_ base]
    (LRUCache. base
               (build-leastness-queue base 0)
               0
               limit))
  Object
  (toString [_]
    (str cache \, \space lru \, \space tick \, \space limit)))


(defn- key-killer-q
  [ttl q expiry now]
  (let [[ks q'] (reduce (fn [[ks q] [k g t]]
                          (if (> (- now t) expiry)
                            (if (= g (first (get ttl k)))
                              [(conj ks k) (pop q)]
                              [ks (pop q)])
                            (reduced [ks q])))
                        [[] q]
                        q)]
    [#(apply dissoc % ks) q']))

(defcache TTLCacheQ [cache ttl q gen ttl-ms]
  CacheProtocol
  (lookup [this item]
    (let [ret (lookup this item ::nope)]
      (when-not (= ::nope ret) ret)))
  (lookup [this item not-found]
    (if (has? this item)
      (get cache item)
      not-found))
  (has? [_ item]
    (and (let [[_ t] (get ttl item [0 (- ttl-ms)])]
           (< (- (System/currentTimeMillis)
                 t)
              ttl-ms))
         (contains? cache item)))
  (hit [this item] this)
  (miss [this item result]
    (let [now  (System/currentTimeMillis)
          [kill-old q'] (key-killer-q ttl q ttl-ms now)]
      (TTLCacheQ. (assoc (kill-old cache) item result)
                  (assoc (kill-old ttl) item [gen now])
                  (conj q' [item gen now])
                  (unchecked-inc gen)
                  ttl-ms)))
  (seed [_ base]
    (let [now (System/currentTimeMillis)]
      (TTLCacheQ. base
                  ;; we seed the cache all at gen, but subsequent entries
                  ;; will get gen+1, gen+2 etc
                  (into {} (for [x base] [(key x) [gen now]]))
                  (into q  (for [x base] [(key x) gen now]))
                  (unchecked-inc gen)
                  ttl-ms)))
  (evict [_ key]
    (TTLCacheQ. (dissoc cache key)
                (dissoc ttl key)
                q
                gen
                ttl-ms))
  Object
  (toString [_]
    (str cache \, \space ttl \, \space ttl-ms)))


(defcache LUCache [cache lu limit]
  CacheProtocol
  (lookup [_ item]
    (get cache item))
  (lookup [_ item not-found]
    (get cache item not-found))
  (has? [_ item]
    (contains? cache item))
  (hit [_ item]
    (LUCache. cache (update-in lu [item] inc) limit))
  (miss [_ item result]
    (if (>= (count lu) limit) ;; need to evict?
      (let [min-key (if (contains? lu item)
                      ::nope
                      (first (peek lu))) ;; maybe evict case
            c (-> cache (dissoc min-key) (assoc item result))
            l (-> lu (dissoc min-key) (update-in [item] (fnil inc 0)))]
        (LUCache. c l limit))
      (LUCache. (assoc cache item result)  ;; no change case
                (update-in lu [item] (fnil inc 0))
                limit)))
  (evict [this key]
    (if (contains? this key)
      (LUCache. (dissoc cache key)
                (dissoc lu key)
                limit)
      this))
  (seed [_ base]
    (LUCache. base
              (build-leastness-queue base 0)
              limit))
  Object
  (toString [_]
    (str cache \, \space lu \, \space limit)))


;; # LIRS
;; *initial Clojure implementation by Jan Oberhagemann*

;;  A
;;  [LIRS](http://citeseer.ist.psu.edu/viewdoc/summary?doi=10.1.1.116.2184)
;;  cache consists of two LRU lists, `S` and `Q`, and keeps more history
;;  than a LRU cache. Every cached item is either a LIR, HIR or
;;  non-resident HIR block. `Q` contains only HIR blocks, `S` contains
;;  LIR, HIR, non-resident HIR blocks. The total cache size is
;;  |`S`|+|`Q`|, |`S`| is typically 99% of the cache size.

;;  * LIR block:
;;    Low Inter-Reference block, a cached item with a short interval
;;    between accesses. A block `x`, `x` ∈ `S` ∧ `x` ∉ `Q` ∧ `x` ∈
;;    `cache`, is a LIR block.

;;  * HIR block:
;;    High Inter-Reference block, a cached item with rare accesses and
;;    long interval. A block `x`, `x` ∈ `Q` ∧ `x` ∈ `cache`, is a HIR block.

;;  * non-resident HIR block:
;;    only the key of the HIR block is cached, without the corresponding
;;    value a test (has?) for the corresponding key is always a
;;    miss. Used for additional history information. A block `x`, `x` ∈
;;    `S` ∧ `x` ∉ `Q` ∧ `x` ∉ `cache`, is a non-resident HIR block.

;; ## Outline of the implemented algorithm

;; `cache` is used to store the key value pairs.
;; `S` and `Q` maintain the relative order of accesses of the keys, like
;; a LRU list.

;; Definition of `prune stack`:
;;
;;         repeatedly remove oldest item from S until an item k, k ∉ Q ∧
;;         k ∈ cache (a LIR block), is found


;; In case of a miss for key `k` and value `v` (`k` ∉ cache) and
;;
;;  * (1.1) `S` is not filled, |`S`| < `limitS`
;;         add k to S
;;         add k to the cache

;;  * (1.2) `k` ∉ `S`, never seen or not seen for a long, long time
;;             remove oldest item x from Q
;;             remove x from cache
;;             add k to S
;;             add k to Q
;;             add k to the cache

;;  * (1.3) `k` ∈ `S`, this is a non-resident HIR block
;;         remove oldest item x from Q
;;         remove x from cache
;;         add k to S
;;         remove oldest item y from S
;;         add y to Q
;;         prune stack


;; In case of a hit for key `k` (`k` ∈ cache) and

;;  * (2.1) `k` ∈ `S` ∧ `k` ∉ `Q`, a LIR block
;;         add k to S / refresh
;;         prune stack if k was the oldest item in S

;;  * (2.2) `k` ∈ `S` ∧ `k` ∈ `Q`, a HIR block
;;         add k to S / refresh
;;         remove k from Q
;;         remove oldest item x from S
;;         add x to Q
;;         prune stack

;;  * (2.3) `k` ∉ `S` ∧ `k` ∈ `Q`, a HIR block, only older than the oldest item in S
;;         add k to S
;;         add k to Q / refresh

(defn- prune-stack [lruS lruQ cache]
  (loop [s lruS q lruQ c cache]
    (let [k (apply min-key s (keys s))]
      (if (or (contains? q k)               ; HIR item
              (not (contains? c k)))        ; non-resident HIR item
        (recur (dissoc s k) q c)
        s))))

(defcache LIRSCache [cache lruS lruQ tick limitS limitQ]
  CacheProtocol
  (lookup [_ item]
    (get cache item))
  (lookup [_ item not-found]
    (get cache item not-found))
  (has? [_ item]
    (contains? cache item))
  (hit [_ item]
    (let [tick+ (inc tick)]
      (if (not (contains? lruS item))
                                        ; (2.3) item ∉ S ∧ item ∈ Q
        (LIRSCache. cache (assoc lruS item tick+) (assoc lruQ item tick+) tick+ limitS limitQ)
        (let [k (apply min-key lruS (keys lruS))]
          (if (contains? lruQ item)
                                        ; (2.2) item ∈ S ∧ item ∈ Q
            (let [new-lruQ (-> lruQ (dissoc item) (assoc k tick+))]
              (LIRSCache. cache
                          (-> lruS (dissoc k) (assoc item tick+) (prune-stack new-lruQ cache))
                          new-lruQ
                          tick+
                          limitS
                          limitQ))
                                        ; (2.1) item ∈ S ∧ item ∉ Q
            (LIRSCache. cache
                        (-> lruS (assoc item tick+) (prune-stack lruQ cache))
                        lruQ
                        tick+
                        limitS
                        limitQ))))))

  (miss [_ item result]
    (let [tick+ (inc tick)]
      (if (< (count cache) limitS)
                                        ; (1.1)
        (let [k (apply min-key lruS (keys lruS))]
          (LIRSCache. (assoc cache item result)
                      (-> lruS (dissoc k) (assoc item tick+))
                      lruQ
                      tick+
                      limitS
                      limitQ))
        (let [k (apply min-key lruQ (keys lruQ))
              new-lruQ (dissoc lruQ k)
              new-cache (-> cache  (dissoc k) (assoc item result))]
          (if (contains? lruS item)
                                        ; (1.3)
            (let [lastS (apply min-key lruS (keys lruS))]
              (LIRSCache. new-cache
                          (-> lruS (dissoc lastS) (assoc item tick+) (prune-stack new-lruQ new-cache))
                          (assoc new-lruQ lastS tick+)
                          tick+
                          limitS
                          limitQ))
                                        ; (1.2)
            (LIRSCache. new-cache
                        (assoc lruS item tick+)
                        (assoc new-lruQ item tick+)
                        tick+
                        limitS
                        limitQ))))))
  (seed [_ base]
    (LIRSCache. base
                (into {} (for [x (range (- limitS) 0)] [x x]))
                (into {} (for [x (range (- limitQ) 0)] [x x]))
                0
                limitS
                limitQ))
  Object
  (toString [_]
    (str cache \, \space lruS \, \space lruQ \, \space tick \, \space limitS \, \space limitQ)))

(defn clear-soft-cache! [^java.util.Map cache ^java.util.Map rcache ^ReferenceQueue rq]
  (loop [r (.poll rq)]
    (when r
      (when-let [item (get rcache r)]
        (.remove cache item))
      (.remove rcache r)
      (recur (.poll rq)))))

(defn make-reference [v rq]
  (if (nil? v)
    (SoftReference. ::nil rq)
    (SoftReference. v rq)))

(defcache SoftCache [^java.util.Map cache ^java.util.Map rcache rq]
  CacheProtocol
  (lookup [_ item]
    (when-let [^SoftReference r (get cache (or item ::nil))]
      (let [v (.get r)]
        (if (= ::nil v)
          nil
          v))))
  (lookup [_ item not-found]
    (if-let [^SoftReference r (get cache (or item ::nil))]
      (if-let [v (.get r)]
        (if (= ::nil v)
          nil
          v)
        not-found)
      not-found))
  (has? [_ item]
    (let [item (or item ::nil)
          ^SoftReference cell (get cache item)]
      (boolean
        (when cell
          (not (nil? (.get cell)))))))
  (hit [this item]
    (clear-soft-cache! cache rcache rq)
    this)
  (miss [this item result]
    (let [item (or item ::nil)
          r (make-reference result rq)]
      (.put cache item r)
      (.put rcache r item)
      (clear-soft-cache! cache rcache rq)
      this))
  (evict [this key]
    (let [key (or key ::nil)
          r (get cache key)]
      (when r
        (.remove cache key)
        (.remove rcache r))
      (clear-soft-cache! cache rcache rq)
      this))
  (seed [_ base]
    (let [soft-cache? (instance? SoftCache base)
          cache (ConcurrentHashMap.)
          rcache (ConcurrentHashMap.)
          rq (ReferenceQueue.)]
      (if (seq base)
        (doseq [[k ^SoftReference v] base]
          (let [k (or k ::nil)
                r (if soft-cache?
                    (make-reference (.get v) rq)
                    (make-reference v rq))]
            (.put cache k r)
            (.put rcache r k))))
      (SoftCache. cache rcache rq)))
  Object
  (toString [_] (str cache)))

;; Factories

(defn basic-cache-factory
  "Returns a pluggable basic cache initialized to `base`"
  [base]
  {:pre [(map? base)]}
  (BasicCache. base))

(defn fifo-cache-factory
  "Returns a FIFO cache with the cache and FIFO queue initialized to `base` --
   the queue is filled as the values are pulled out of `base`.  If the associative
   structure can guarantee ordering, then the said ordering will define the
   eventual eviction order.  Otherwise, there are no guarantees for the eventual
   eviction ordering.

   This function takes an optional `:threshold` argument that defines the maximum number
   of elements in the cache before the FIFO semantics apply (default is 32).

   If the number of elements in `base` is greater than the limit then some items
   in `base` will be dropped from the resulting cache.  If the associative
   structure used as `base` can guarantee sorting, then the last `limit` elements
   will be used as the cache seed values.  Otherwise, there are no guarantees about
   the elements in the resulting cache."
  [base & {threshold :threshold :or {threshold 32}}]
  {:pre [(number? threshold) (< 0 threshold)
         (map? base)]
   :post [(== threshold (count (.q ^FIFOCache %)))]}
  (nextjournal.clerk.clojure.core.cache/seed (FIFOCache. {} clojure.lang.PersistentQueue/EMPTY threshold) base))

(defn lru-cache-factory
  "Returns an LRU cache with the cache and usage-table initialized to `base` --
   each entry is initialized with the same usage value.

   This function takes an optional `:threshold` argument that defines the maximum number
   of elements in the cache before the LRU semantics apply (default is 32)."
  [base & {threshold :threshold :or {threshold 32}}]
  {:pre [(number? threshold) (< 0 threshold)
         (map? base)]}
  (nextjournal.clerk.clojure.core.cache/seed (LRUCache. {} (clojure.data.priority-map/priority-map) 0 threshold) base))

(defn ttl-cache-factory
  "Returns a TTL cache with the cache and expiration-table initialized to `base` --
   each with the same time-to-live.

   This function also allows an optional `:ttl` argument that defines the default
   time in milliseconds that entries are allowed to reside in the cache."
  [base & {ttl :ttl :or {ttl 2000}}]
  {:pre [(number? ttl) (<= 0 ttl)
         (map? base)]}
  (nextjournal.clerk.clojure.core.cache/seed (TTLCacheQ. {} {} clojure.lang.PersistentQueue/EMPTY 0 ttl) base))

(defn lu-cache-factory
  "Returns an LU cache with the cache and usage-table initialized to `base`.

   This function takes an optional `:threshold` argument that defines the maximum number
   of elements in the cache before the LU semantics apply (default is 32)."
  [base & {threshold :threshold :or {threshold 32}}]
  {:pre [(number? threshold) (< 0 threshold)
         (map? base)]}
  (nextjournal.clerk.clojure.core.cache/seed (LUCache. {} (clojure.data.priority-map/priority-map) threshold) base))

(defn lirs-cache-factory
  "Returns an LIRS cache with the S & R LRU lists set to the indicated
   limits."
  [base & {:keys [s-history-limit q-history-limit]
           :or {s-history-limit 32
                q-history-limit 32}}]
  {:pre [(number? s-history-limit) (< 0 s-history-limit)
         (number? q-history-limit) (< 0 q-history-limit)
         (map? base)]}
  (nextjournal.clerk.clojure.core.cache/seed (LIRSCache. {} {} {} 0 s-history-limit q-history-limit) base))

(defn soft-cache-factory
  "Returns a SoftReference cache.  Cached values will be referred to with
  SoftReferences, allowing the values to be garbage collected when there is
  memory pressure on the JVM.

  SoftCache is a mutable cache, since it is always based on a
  ConcurrentHashMap."
  [base]
  {:pre [(map? base)]}
  (nextjournal.clerk.clojure.core.cache/seed (SoftCache. (ConcurrentHashMap.) (ConcurrentHashMap.) (ReferenceQueue.))
        base))
