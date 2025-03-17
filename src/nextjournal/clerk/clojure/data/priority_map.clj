;;   Copyright (c) Mark Engelberg, Rich Hickey and contributors. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

;; A priority map is a map from items to priorities,
;; offering queue-like peek/pop as well as the map-like ability to
;; easily reassign priorities and other conveniences.
;; by Mark Engelberg (mark.engelberg@gmail.com)
;; Last update - September 19, 2021

(ns 
    ^{:author "Mark Engelberg",
      :doc "A priority map is very similar to a sorted map, but whereas a sorted map produces a
sequence of the entries sorted by key, a priority map produces the entries sorted by value.
In addition to supporting all the functions a sorted map supports, a priority map
can also be thought of as a queue of [item priority] pairs.  To support usage as
a versatile priority queue, priority maps also support conj/peek/pop operations.

The standard way to construct a priority map is with priority-map:
user=> (def p (priority-map :a 2 :b 1 :c 3 :d 5 :e 4 :f 3))
#'user/p
user=> p
{:b 1, :a 2, :c 3, :f 3, :e 4, :d 5}

So :b has priority 1, :a has priority 2, and so on.
Notice how the priority map prints in an order sorted by its priorities (i.e., the map's values)

We can use assoc to assign a priority to a new item:
user=> (assoc p :g 1)
{:b 1, :g 1, :a 2, :c 3, :f 3, :e 4, :d 5}

or to assign a new priority to an extant item:
user=> (assoc p :c 4)
{:b 1, :a 2, :f 3, :c 4, :e 4, :d 5}

We can remove an item from the priority map:
user=> (dissoc p :e)
{:b 1, :a 2, :c 3, :f 3, :d 5}

An alternative way to add to the priority map is to conj a [item priority] pair:
user=> (conj p [:g 0])
{:g 0, :b 1, :a 2, :c 3, :f 3, :e 4, :d 5}

or use into:
user=> (into p [[:g 0] [:h 1] [:i 2]])
{:g 0, :b 1, :h 1, :a 2, :i 2, :c 3, :f 3, :e 4, :d 5}

Priority maps are countable:
user=> (count p)
6

Like other maps, equivalence is based not on type, but on contents.
In other words, just as a sorted-map can be equal to a hash-map,
so can a priority-map.
user=> (= p {:b 1, :a 2, :c 3, :f 3, :e 4, :d 5})
true

You can test them for emptiness:
user=> (empty? (priority-map))
true
user=> (empty? p)
false

You can test whether an item is in the priority map:
user=> (contains? p :a)
true
user=> (contains? p :g)
false

It is easy to look up the priority of a given item, using any of the standard map mechanisms:
user=> (get p :a)
2
user=> (get p :g 10)
10
user=> (p :a)
2
user=> (:a p)
2

Priority maps derive much of their utility by providing priority-based seq.
Note that no guarantees are made about the order in which items of the same priority appear.
user=> (seq p)
([:b 1] [:a 2] [:c 3] [:f 3] [:e 4] [:d 5])
Because no guarantees are made about the order of same-priority items, note that
rseq might not be an exact reverse of the seq.  It is only guaranteed to be in
descending order.
user=> (rseq p)
([:d 5] [:e 4] [:c 3] [:f 3] [:a 2] [:b 1])

This means first/rest/next/for/map/etc. all operate in priority order.
user=> (first p)
[:b 1]
user=> (rest p)
([:a 2] [:c 3] [:f 3] [:e 4] [:d 5])

Priority maps also support subseq and rsubseq, however, *you must use the subseq and rsubseq
defined in the clojure.data.priority-map namespace*, which patches longstanding JIRA issue
[CLJ-428](https://clojure.atlassian.net/browse/CLJ-428).  These patched versions
of subseq and rsubseq will work on Clojure's other sorted collections as well, so you can
use them as a drop-in replacement for the subseq and rsubseq found in core.
user=> (subseq p < 3)
([:b 1] [:a 2])
user=> (subseq p >= 3)
([:c 3] [:f 3] [:e 4] [:d 5])
user=> (subseq p >= 2 < 4)
([:a 2] [:c 3] [:f 3])
user=> (rsubseq p < 4)
([:c 3] [:f 3] [:a 2] [:b 1])
user=> (rsubseq p >= 4)
([:d 5] [:e 4])

Priority maps support metadata:
user=> (meta (with-meta p {:extra :info}))
{:extra :info}

But perhaps most importantly, priority maps can also function as priority queues.
peek, like first, gives you the first [item priority] pair in the collection.
pop removes the first [item priority] from the collection.
(Note that unlike rest, which returns a seq, pop returns a priority map).

user=> (peek p)
[:b 1]
user=> (pop p)
{:a 2, :c 3, :f 3, :e 4, :d 5}

It is also possible to use a custom comparator:
user=> (priority-map-by > :a 1 :b 2 :c 3)
{:c 3, :b 2, :a 1}

Sometimes, it is desirable to have a map where the values contain more information
than just the priority.  For example, let's say you want a map like:
{:a [2 :apple], :b [1 :banana], :c [3 :carrot]}
and you want to sort the map by the numeric priority found in the pair.

A common mistake is to try to solve this with a custom comparator:
(priority-map-by 
  (fn [[priority1 _] [priority2 _]] (< priority1 priority2))
  :a [2 :apple], :b [1 :banana], :c [3 :carrot])

This will not work!  Although it may appear to work with these particular values, it is not safe.
In Clojure, like Java, all comparators must be *total orders*,
meaning that you can't have a tie unless the objects you are comparing are
in fact equal.  The above comparator breaks that rule because objects such as
`[2 :apple]` and `[2 :apricot]` would tie, but are not equal.

The correct way to construct such a priority map is by specifying a keyfn, which is used
to extract the true priority from the priority map's vals.  (Note: It might seem a little odd
that the priority-extraction function is called a *key*fn, even though it is applied to the
map's values.  This terminology is based on the docstring of clojure.core/sort-by, which
uses `keyfn` for the function which extracts the sort order.) 

In the above example,

user=> (priority-map-keyfn first :a [2 :apple], :b [1 :banana], :c [3 :carrot])
{:b [1 :banana], :a [2 :apple], :c [3 :carrot]}

You can also combine a keyfn with a comparator that operates on the extracted priorities:

user=> (priority-map-keyfn-by 
          first >
          :a [2 :apple], :b [1 :banana], :c [3 :carrot])
{:c [3 :carrot], :a [2 :apple], :b [1 :banana]}

 

All of these operations are efficient.  Generally speaking, most operations
are O(log n) where n is the number of distinct priorities.  Some operations
(for example, straightforward lookup of an item's priority, or testing
whether a given item is in the priority map) are as efficient
as Clojure's built-in map.

The key to this efficiency is that internally, not only does the priority map store
an ordinary hash map of items to priority, but it also stores a sorted map that
maps priorities to sets of items with that priority.

A typical textbook priority queue data structure supports at the ability to add
a [item priority] pair to the queue, and to pop/peek the next [item priority] pair.
But many real-world applications of priority queues require more features, such
as the ability to test whether something is already in the queue, or to reassign
a priority.  For example, a standard formulation of Dijkstra's algorithm requires the
ability to reduce the priority number associated with a given item.  Once you
throw persistence into the mix with the desire to adjust priorities, the traditional
structures just don't work that well.

This particular blend of Clojure's built-in hash sets, hash maps, and sorted maps
proved to be a great way to implement an especially flexible persistent priority queue.

Connoisseurs of algorithms will note that this structure's peek operation is not O(1) as
it would be if based upon a heap data structure, but I feel this is a small concession for
the blend of persistence, priority reassignment, and priority-sorted seq, which can be
quite expensive to achieve with a heap (I did actually try this for comparison).  Furthermore,
this peek's logarithmic behavior is quite good (on my computer I can do a million
peeks at a priority map with a million items in 750ms).  Also, consider that peek and pop
usually follow one another, and even with a heap, pop is logarithmic.  So the net combination
of peek and pop is not much different between this versatile formulation of a priority map and
a more limited heap-based one.  In a nutshell, peek, although not O(1), is unlikely to be the
bottleneck in your program.

All in all, I hope you will find priority maps to be an easy-to-use and useful addition
to Clojure's assortment of built-in maps (hash-map and sorted-map).
"}
    nextjournal.clerk.clojure.data.priority-map
  (:refer-clojure :exclude [subseq rsubseq])
  (:import clojure.lang.MapEntry java.util.Map clojure.lang.PersistentTreeMap))

(declare pm-empty)

(defmacro apply-keyfn [x]
  `(if ~'keyfn (~'keyfn ~x) ~x)) 

(defmacro ^:private compile-if [test then else]
  (if (eval test)
    then
    else))

;; We create a patched version of subseq and rsubseq from core, that works on ordinary sorted collections, as well as priority maps
;; See https://dev.clojure.org/jira/browse/CLJ-428

(defn mk-bound-fn
  {:private true}
  [^clojure.lang.Sorted sc test key]
  (fn [e] (test (.. sc comparator (compare (. sc entryKey e) key)) 0)))

(defn subseq
  "sc must be a sorted collection, test(s) one of <, <=, > or
  >=. Returns a seq of those entries with keys ek for
  which (test (.. sc comparator (compare ek key)) 0) is true"
  ([^clojure.lang.Sorted sc test key]
   (let [include (mk-bound-fn sc test key)]
     (if (#{> >=} test)
       (when-let [[e :as s] (. sc seqFrom key true)]
         (seq (drop-while #(not (include %)) s)))
       (seq (take-while include (. sc seq true))))))
  ([^clojure.lang.Sorted sc start-test start-key end-test end-key]
   (when-let [[e :as s] (. sc seqFrom start-key true)]
     (seq (take-while (mk-bound-fn sc end-test end-key)
                      (drop-while (complement (mk-bound-fn sc start-test start-key)) s))))))

(defn rsubseq
  "sc must be a sorted collection, test(s) one of <, <=, > or
  >=. Returns a reverse seq of those entries with keys ek for
  which (test (.. sc comparator (compare ek key)) 0) is true"
  ([^clojure.lang.Sorted sc test key]
   (let [include (mk-bound-fn sc test key)]
     (if (#{< <=} test)
       (when-let [[e :as s] (. sc seqFrom key false)]
         (seq (drop-while #(not (include %)) s)))
       (seq (take-while include (. sc seq false))))))
  ([^clojure.lang.Sorted sc start-test start-key end-test end-key]
   (when-let [[e :as s] (. sc seqFrom end-key false)]
     (seq (take-while (mk-bound-fn sc start-test start-key)
                      (drop-while (complement (mk-bound-fn sc end-test end-key)) s))))))

;; A Priority Map is comprised of a sorted map that maps priorities to hash sets of items
;; with that priority (priority->set-of-items),
;; as well as a hash map that maps items to priorities (item->priority)
;; Priority maps may also have metadata
;; Priority maps can also have a keyfn which is applied to the "priorities" found as values in 
;; the item->priority map to get the actual sortable priority keys used in priority->set-of-items.

(deftype PersistentPriorityMap [priority->set-of-items item->priority _meta keyfn]
  Object
  (toString [this] (str (.seq this)))

  clojure.lang.ILookup
  ;; valAt gives (get pm key) and (get pm key not-found) behavior
  (valAt [this item] (get item->priority item))
  (valAt [this item not-found] (get item->priority item not-found))

  clojure.lang.IPersistentMap
  (count [this] (count item->priority))

  (assoc [this item priority]
    (let [current-priority (get item->priority item nil)]
      (if current-priority
        ;;Case 1 - item is already in priority map, so this is a reassignment
        (if (= current-priority priority)
          ;;Subcase 1 - no change in priority, do nothing
          this
          (let [priority-key (apply-keyfn priority)
                current-priority-key (apply-keyfn current-priority)
                item-set (get priority->set-of-items current-priority-key)]
            (if (= (count item-set) 1)
              ;;Subcase 2 - it was the only item of this priority
              ;;so remove old priority entirely
              ;;and conj item onto new priority's set
              (PersistentPriorityMap.
               (assoc (dissoc priority->set-of-items current-priority-key)
                      priority-key (conj (get priority->set-of-items priority-key #{}) item))
               (assoc item->priority item priority)
               (meta this)
               keyfn)
              ;;Subcase 3 - there were many items associated with the item's original priority,
              ;;so remove it from the old set and conj it onto the new one.
              (PersistentPriorityMap.
               (assoc priority->set-of-items
                      current-priority-key (disj (get priority->set-of-items current-priority-key) item)
                      priority-key (conj (get priority->set-of-items priority-key #{}) item))
               (assoc item->priority item priority)
               (meta this)
               keyfn))))
        ;; Case 2: Item is new to the priority map, so just add it.
        (let [priority-key (apply-keyfn priority)]
          (PersistentPriorityMap.
           (assoc priority->set-of-items
                  priority-key (conj (get priority->set-of-items priority-key #{}) item))
           (assoc item->priority item priority)
           (meta this)
           keyfn)))))

  (empty [this] (PersistentPriorityMap. (empty priority->set-of-items) {} _meta keyfn))

  ;; cons defines conj behavior
  (cons [this e] 
    (if (map? e)
      (into this e)
      (let [[item priority] e] (.assoc this item priority))))

  ;; Like sorted maps, priority maps are equal to other maps provided
  ;; their key-value pairs are the same.
  (equiv [this o] (= item->priority o))
  (hashCode [this] (.hashCode item->priority))
  (equals [this o] (or (identical? this o) (.equals item->priority o)))

  ;;containsKey implements (contains? pm k) behavior
  (containsKey [this item] (contains? item->priority item))

  (entryAt [this k]
    (let [v (.valAt this k this)]
      (when-not (identical? v this)
        (MapEntry. k v))))

  (seq [this]
    (if keyfn
      (seq (for [[priority item-set] priority->set-of-items, item item-set]
             (MapEntry. item (item->priority item))))
      (seq (for [[priority item-set] priority->set-of-items, item item-set]
             (MapEntry. item priority)))))

  ;;without implements (dissoc pm k) behavior
  (without
    [this item]
    (let [priority (item->priority item ::not-found)]
      (if (= priority ::not-found)
        ;; If item is not in map, return the map unchanged.
        this
        (let [priority-key (apply-keyfn priority)
              item-set (priority->set-of-items priority-key)]
          (if (= (count item-set) 1)
            ;;If it is the only item with this priority, remove that priority's set completely
            (PersistentPriorityMap. (dissoc priority->set-of-items priority-key)
                                    (dissoc item->priority item)
                                    (meta this)
                                    keyfn)
            ;;Otherwise, just remove the item from the priority's set.
            (PersistentPriorityMap.
             (assoc priority->set-of-items priority-key (disj item-set item)),
             (dissoc item->priority item)
             (meta this)
             keyfn))))))
  
  clojure.lang.IHashEq
  (hasheq [this]
    (compile-if (resolve 'clojure.core/hash-unordered-coll)
                (hash-unordered-coll this)
                (.hashCode this)))
  
  java.io.Serializable  ;Serialization comes for free with the other things implemented
  clojure.lang.MapEquivalence
  Map ;Makes this compatible with java's map
  (size [this] (count item->priority))
  (isEmpty [this] (zero? (count item->priority)))
  (containsValue [this v] 
    (if keyfn
      (some (partial = v) (vals this)) ; no shortcut if there is a keyfn
      (contains? priority->set-of-items v)))
  (get [this k] (.valAt this k))
  (put [this k v] (throw (UnsupportedOperationException.)))
  (remove [this k] (throw (UnsupportedOperationException.)))
  (putAll [this m] (throw (UnsupportedOperationException.)))
  (clear [this] (throw (UnsupportedOperationException.)))
  (keySet [this] (set (keys this)))
  (values [this] (vals this))
  (entrySet [this] (set this))
  
  Iterable
  (iterator [this] (clojure.lang.SeqIterator. (seq this)))

  clojure.core.protocols/IKVReduce
  (kv-reduce [this f init]
    (if keyfn            
      (reduce-kv (fn [a k v]
                   (reduce (fn [a v] (f a v (item->priority v))) a v))
                 init priority->set-of-items)
      (reduce-kv (fn [a k v]
                   (reduce (fn [a v] (f a v k)) a v))
                 init priority->set-of-items)))

  clojure.lang.IPersistentStack
  (peek [this]
    (when-not (.isEmpty this)
      (let [f (first priority->set-of-items)
            item (first (val f))]
        (if keyfn
          (MapEntry. item (item->priority item)) 
          (MapEntry. item (key f))))))

  (pop [this]
    (if (.isEmpty this) (throw (IllegalStateException. "Can't pop empty priority map"))
        (let [f (first priority->set-of-items),
              item-set (val f)
              item (first item-set),
              priority-key (key f)]
          (if (= (count item-set) 1)
            ;;If the first item is the only item with its priority, remove that priority's set completely
            (PersistentPriorityMap.
             (dissoc priority->set-of-items priority-key)
             (dissoc item->priority item)
             (meta this)
             keyfn)
            ;;Otherwise, just remove the item from the priority's set.
            (PersistentPriorityMap.
             (assoc priority->set-of-items priority-key (disj item-set item)),
             (dissoc item->priority item)
             (meta this)
             keyfn)))))

  clojure.lang.IFn
  ;;makes priority map usable as a function
  (invoke [this k] (.valAt this k))
  (invoke [this k not-found] (.valAt this k not-found))

  clojure.lang.IObj
  ;;adds metadata support
  (meta [this] _meta)
  (withMeta [this m] (PersistentPriorityMap. priority->set-of-items item->priority m keyfn))

  clojure.lang.Reversible
  (rseq [this]
    (if keyfn
      (seq (for [[priority item-set] (rseq priority->set-of-items), item item-set]
             (MapEntry. item (item->priority item))))
      (seq (for [[priority item-set] (rseq priority->set-of-items), item item-set]
             (MapEntry. item priority)))))

  clojure.lang.Sorted
  ;; These methods provide support for subseq and rsubseq
  (comparator [this] (.comparator ^PersistentTreeMap priority->set-of-items))
  (entryKey [this entry] (if keyfn (keyfn (val entry)) (val entry)))
  (seqFrom [this k ascending]
    (let [sets (if ascending (subseq priority->set-of-items >= k) (rsubseq priority->set-of-items <= k))]
      (if keyfn
        (seq (for [[priority item-set] sets, item item-set]
               (MapEntry. item (item->priority item))))
        (seq (for [[priority item-set] sets, item item-set]
               (MapEntry. item priority))))))
  (seq [this ascending]
    (if ascending (seq this) (rseq this))))

(def ^:private pm-empty (PersistentPriorityMap. (sorted-map) {} {} nil))
(defn- pm-empty-by [comparator] (PersistentPriorityMap. (sorted-map-by comparator) {} {} nil))
(defn- pm-empty-keyfn
  ([keyfn] (PersistentPriorityMap. (sorted-map) {} {} keyfn))
  ([keyfn comparator] (PersistentPriorityMap. (sorted-map-by comparator) {} {} keyfn)))


;; The main way to build priority maps
(defn priority-map
  "Usage: (priority-map key val key val ...)
  Returns a new priority map with optional supplied mappings.
  (priority-map) returns an empty priority map."
  [& keyvals]
  {:pre [(even? (count keyvals))]}
  (reduce conj pm-empty (partition 2 keyvals)))

(defn priority-map-by
  "Usage: (priority-map comparator key val key val ...)
  Returns a new priority map with custom comparator and optional supplied mappings.
  (priority-map-by comparator) yields an empty priority map with custom comparator."
  [comparator & keyvals]
  {:pre [(even? (count keyvals))]}
  (reduce conj (pm-empty-by comparator) (partition 2 keyvals)))

(defn priority-map-keyfn
  "Usage: (priority-map-keyfn keyfn key val key val ...)
  Returns a new priority map with custom keyfn and optional supplied mappings.
  The priority is determined by comparing (keyfn val). 
  (priority-map-keyfn keyfn) yields an empty priority map with custom keyfn."
  [keyfn & keyvals]
  {:pre [(even? (count keyvals))]}
  (reduce conj (pm-empty-keyfn keyfn) (partition 2 keyvals)))

(defn priority-map-keyfn-by
  "Usage: (priority-map-keyfn-by keyfn comparator key val key val ...)
  Returns a new priority map with custom keyfn, custom comparator, and optional supplied mappings.
  The priority is determined by comparing (keyfn val).
  (priority-map-keyfn-by keyfn comparator) yields an empty priority map with custom keyfn and comparator."
  [keyfn comparator & keyvals]
  {:pre [(even? (count keyvals))]}
  (reduce conj (pm-empty-keyfn keyfn comparator) (partition 2 keyvals)))

(defn priority->set-of-items
  "Takes a priority map p, and returns a sorted map from each priority
  to the set of items with that priority in p"
  [^PersistentPriorityMap p]
  (.priority->set-of-items p))
