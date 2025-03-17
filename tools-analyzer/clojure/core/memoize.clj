;   Copyright (c) Rich Hickey and Michael Fogus. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.core.memoize
  "core.memoize is a memoization library offering functionality above
  Clojure's core `memoize` function in the following ways:

  **Pluggable memoization**

  core.memoize allows for different back-end cache implmentations to
  be used as appropriate without changing the memoization modus operandi.
  See the `memoizer` function.

  **Manipulable memoization**

  Because core.memoize allows you to access a function's memoization store,
  you do interesting things like clear it, modify it, and save it for later.
  "
  {:author "fogus"}

  (:require [nextjournal.clerk.clojure.core.cache :as cache]))



;; Similar to clojure.lang.Delay, but will not memoize an exception and will
;; instead retry.
;;   fun - the function, never nil
;;   available? - indicates a memoized value is available, volatile for visibility
;;   value - the value (if available) - volatile for visibility
(deftype RetryingDelay [fun ^:volatile-mutable available? ^:volatile-mutable value]
  clojure.lang.IDeref
  (deref [this]
    ;; first check (safe with volatile flag)
    (if available?
      value
      (locking fun
        ;; second check (race condition with locking)
        (if available?
          value
          (do
            ;; fun may throw - will retry on next deref
            (let [v (fun)]
              ;; this ordering is important - MUST set value before setting available?
              ;; or you have a race with the first check above
              (set! value v)
              (set! available? true)
              v))))))
  clojure.lang.IPending
  (isRealized [this]
    available?))

(defn- d-lay [fun]
  (->RetryingDelay fun false nil))

(defn- make-derefable
  "If a value is not already derefable, wrap it up.

  This is used to help rebuild seed/base maps passed in to the various
  caches so that they conform to core.memoize's world view."
  [v]
  (if (instance? clojure.lang.IDeref v)
    v
    (reify clojure.lang.IDeref
      (deref [_] v))))

(defn- derefable-seed
  "Given a seed/base map, ensure all the values in it are derefable."
  [seed]
  (into {} (for [[k v] seed] [k (make-derefable v)])))

;; Plugging Interface

(deftype PluggableMemoization [f cache]
  cache/CacheProtocol
  (has? [_ item]
    (nextjournal.clerk.clojure.core.cache/has? cache item))
  (hit  [_ item]
    (PluggableMemoization. f (nextjournal.clerk.clojure.core.cache/hit cache item)))
  (miss [_ item result]
    (PluggableMemoization. f (nextjournal.clerk.clojure.core.cache/miss cache item result)))
  (evict [_ key]
    (PluggableMemoization. f (nextjournal.clerk.clojure.core.cache/evict cache key)))
  (lookup [_ item]
    (nextjournal.clerk.clojure.core.cache/lookup cache item nil))
  (lookup [_ item not-found]
    (nextjournal.clerk.clojure.core.cache/lookup cache item (delay not-found)))
  (seed [_ base]
    (PluggableMemoization.
     f (nextjournal.clerk.clojure.core.cache/seed cache (derefable-seed base))))
  Object
  (toString [_] (str cache)))

;; # Auxilliary functions

(def ^{:private true
       :doc "Returns a function's argument transformer."}
  args-fn #(or (::args-fn (meta %)) identity))

(defn- through*
  "The basic hit/miss logic for the cache system based on `core.cache/through`.
  Clojure delays are used to hold the cache value."
  [cache f args item]
  (nextjournal.clerk.clojure.core.cache/through
   (fn [f _] (d-lay #(f args)))
   #(clojure.core/apply f %)
   cache
   item))

(def ^{:private true
       :doc "Returns a function's cache identity."}
  cache-id #(::cache (meta %)))


;; # Public Utilities API

(defn snapshot
  "Returns a snapshot of a core.memo-placed memoization cache.  By snapshot
   you can infer that what you get is only the cache contents at a
   moment in time."
  [memoized-fn]
  (when-let [cache (cache-id memoized-fn)]
    (into {}
          (for [[k v] (.cache ^PluggableMemoization @cache)]
            [(vec k) @v]))))

(defn lazy-snapshot
  "Returns a lazy snapshot of a core.memo-placed memoization cache.  By
   lazy snapshot you can infer that what you get is only the cache contents at a
   moment in time -- and, being lazy, the cache could change while you are
   realizing the snapshot elements.

   Returns a sequence of key/value pairs."
  [memoized-fn]
  (when-let [cache (cache-id memoized-fn)]
    (for [[k v] (.cache ^PluggableMemoization @cache)]
      [(vec k) @v])))

(defn memoized?
  "Returns true if a function has an core.memo-placed cache, false otherwise."
  [f]
  (boolean (cache-id f)))

(defn memo-clear!
  "Reaches into an core.memo-memoized function and clears the cache.  This is a
   destructive operation and should be used with care.

   When the second argument is a vector of input arguments, clears cache only
   for argument vector.

   Keep in mind that depending on what other threads or doing, an
   immediate call to `snapshot` may not yield an empty cache.  That's
   cool though, we've learned to deal with that stuff in Clojure by
   now."
  ([f]
   (when-let [cache (cache-id f)]
     (swap! cache (constantly (nextjournal.clerk.clojure.core.cache/seed @cache {})))))
  ([f args]
   (when-let [cache (cache-id f)]
     (swap! cache (constantly (nextjournal.clerk.clojure.core.cache/evict @cache args))))))

(defn memo-reset!
  "Takes a core.memo-populated function and a map and replaces the memoization cache
   with the supplied map.  This is potentially some serious voodoo,
   since you can effectively change the semantics of a function on the fly.

       (def id (memo identity))
       (memo-swap! id '{[13] :omg})
       (id 13)
       ;=> :omg

   With great power comes ... yadda yadda yadda."
  [f base]
  (when-let [cache (cache-id f)]
    (swap! cache
           (constantly (nextjournal.clerk.clojure.core.cache/seed @cache (derefable-seed base))))))

(defn memo-swap!
  "The 2-arity version takes a core.memo-populated function and a map and
  replaces the memoization cache with the supplied map. Use `memo-reset!`
  instead for replacing the cache as this 2-arity version of `memo-swap!`
  should be considered deprecated.

  The 3+-arity version takes a core.memo-populated function and arguments
  similar to what you would pass to `clojure.core/swap!` and performs a
  `swap!` on the underlying cache. In order to satisfy core.memoize's
  world view, the assumption is that you will generally be calling it like:

        (def id (memo identity))
        (memo-swap! id clojure.core.cache/miss [13] :omg)
        (id 13)
        ;=> :omg

  You'll nearly always use `clojure.core.cache/miss` for this operation but
  you could pass any function that would work on an immutable cache, such
  as `evict` or `assoc` etc.

  Be aware that `memo-swap!` assumes it can wrap each of the `results` values
  in a `delay` so that items conform to `clojure.core.memoize`'s world view."
  ([f base]
   (when-let [cache (cache-id f)]
     (swap! cache
            (constantly (nextjournal.clerk.clojure.core.cache/seed @cache (derefable-seed base))))))
  ([f swap-fn args & results]
   (when-let [cache (cache-id f)]
     (apply swap! cache swap-fn args (map #(delay %) results)))))

(defn memo-unwrap
  [f]
  (::original (meta f)))

(defn- cached-function
  "Given a function, an atom containing a (pluggable memoization cache), and
  and cache key function, return a new function that behaves like the original
  function except it is cached, based on its arguments, with the cache and the
  original function in its metadata."
  [f cache-atom ckey-fn]
  (with-meta
   (fn [& args]
     (let [ckey (or (ckey-fn args) [])
           cs   (swap! cache-atom through* f args ckey)
           val  (nextjournal.clerk.clojure.core.cache/lookup cs ckey ::not-found)]
       ;; If `lookup` returns `(delay ::not-found)`, it's likely that
       ;; we ran into a timing issue where eviction and access
       ;; are happening at about the same time. Therefore, we retry
       ;; the `swap!` (potentially several times).
       ;;
       ;; core.memoize currently wraps all of its values in a `delay`.
       (when val
         (loop [n 0 v @val]
           (if (= ::not-found v)
             (when-let [v' (nextjournal.clerk.clojure.core.cache/lookup
                            (swap! cache-atom through* f args ckey)
                            ckey ::not-found)]
               (when (< n 10)
                 (recur (inc n) @v')))
             v)))))
   {::cache cache-atom
    ::original f}))

;; # Public memoization API

(defn memoizer
  "Build a pluggable memoized version of a function. Given a function and a
  (pluggable memoized) cache, and an optional seed (hash map of arguments to
  return values), return a cached version of that function.

  If you want to build your own cached function, perhaps with combined caches
  or customized caches, this is the preferred way to do so now."
  ([f cache]
   (let [cache   (atom (PluggableMemoization. f cache))
         ckey-fn (args-fn f)]
     (cached-function f cache ckey-fn)))
  ([f cache seed]
   (let [cache   (atom (nextjournal.clerk.clojure.core.cache/seed (PluggableMemoization. f cache)
                                                (derefable-seed seed)))
         ckey-fn (args-fn f)]
     (cached-function f cache ckey-fn))))

(defn build-memoizer
  "Builds a function that, given a function, returns a pluggable memoized
   version of it.  `build-memoizer` takes a cache factory function, and the
   arguments to that factory function -- at least one of those arguments
   should be the function to be memoized (it's usually the first argument).

  `memoizer` above is a simpler version of `build-memoizer` that 'does the
  right thing' with a cache and a seed hash map. `build-memoizer` remains
  for backward compatibility but should be considered deprecated."
  ([cache-factory f & args]
   (let [cache   (atom (apply cache-factory f args))
         ckey-fn (args-fn f)]
     (cached-function f cache ckey-fn))))

(defn memo
  "Used as a more flexible alternative to Clojure's core `memoization`
   function.  Memoized functions built using `memo` will respond to
   the core.memo manipulable memoization utilities.  As a nice bonus,
   you can use `memo` in place of `memoize` without any additional
   changes, with the added guarantee that the memoized function will
   only be called once for a given sequence of arguments (`memoize`
   can call the function multiple times when concurrent calls are
   made with the same sequence of arguments).

   The default way to use this function is to simply supply a function
   that will be memoized.  Additionally, you may also supply a map
   of the form `'{[42] 42, [108] 108}` where keys are a vector
   mapping expected argument values to arity positions.  The map values
   are the return values of the memoized function.

   If the supplied function has metadata containing an
   `:clojure.core.memoize/args-fn` key, the value is assumed to be a
   function that should be applied to the arguments to produce a
   subset or transformed sequence of arguments that are used for the
   key in the cache (the full, original arguments will still be used
   to call the function). This allows you to memoize functions where
   one or more arguments are irrelevant for memoization, such as the
   `clojure.java.jdbc` functions, whose first argument may include
   a (mutable) JDBC `Connection` object:

     (memo/memo (with-meta jdbc/execute! {::memo/args-fn rest}))

   You can access the memoization cache directly via the `:clojure.core.memoize/cache` key
   on the memoized function's metadata.  However, it is advised to
   use the core.memo primitives instead as implementation details may
   change over time."
  ([f] (memo f {}))
  ([f seed]
   (memoizer f (cache/basic-cache-factory {}) seed)))

;; ## Utilities

(defn ^{:private true} !! [c]
  (println "WARNING - Deprecated construction method for"
           c
           "cache; prefered way is:"
           (str "(clojure.core.memoize/" c " function <base> <:" c "/threshold num>)")))

(defmacro ^{:private true} def-deprecated [nom ds & arities]
  `(defn ~(symbol (str "memo-" (name nom))) ~ds
      ~@(for [[args body] arities]
          (list args `(!! (quote ~nom)) body))))

(defmacro ^{:private true} massert [condition msg]
  `(when-not ~condition
      (throw (new AssertionError (str "clojure.core.memoize/" ~msg "\n" (pr-str '~condition))))))

(defmacro ^{:private true} check-args [nom f base key threshold]
  (when *assert*
    (let [good-key (keyword nom "threshold")
          key-error `(str "Incorrect threshold key " ~key)
          fun-error `(str ~nom " expects a function as its first argument; given " ~f)
          thresh-error `(str ~nom " expects an integer for its " ~good-key " argument; given " ~threshold)]
      `(do (massert (= ~key ~good-key) ~key-error)
           (massert (some #{clojure.lang.IFn
                            clojure.lang.AFn
                            java.lang.Runnable
                            java.util.concurrent.Callable}
                          (ancestors (class ~f)))
                    ~fun-error)
           (massert (number? ~threshold) ~thresh-error)))))

;; ## Main API functions

;; ### FIFO

(def-deprecated fifo
  "DEPRECATED: Please use clojure.core.memoize/fifo instead."
  ([f] (memo-fifo f 32 {}))
  ([f limit] (memo-fifo f limit {}))
  ([f limit base]
   (memoizer f (cache/fifo-cache-factory {} :threshold limit) base)))

(defn fifo
  "Works the same as the basic memoization function (i.e. `memo`
   and `core.memoize` except when a given threshold is breached.

   Observe the following:

       (require '[clojure.core.memoize :as memo])

       (def id (memo/fifo identity :fifo/threshold 2))

       (id 42)
       (id 43)
       (snapshot id)
       ;=> {[42] 42, [43] 43}

   As you see, the limit of `2` has not been breached yet, but
   if you call again with another value, then it is:

       (id 44)
       (snapshot id)
       ;=> {[44] 44, [43] 43}

   That is, the oldest entry `42` is pushed out of the
   memoization cache.  This is the standard **F**irst **I**n
   **F**irst **O**ut behavior."
  ([f] (fifo f {} :fifo/threshold 32))
  ([f base] (fifo f base :fifo/threshold 32))
  ([f tkey threshold] (fifo f {} tkey threshold))
  ([f base key threshold]
   (check-args "fifo" f base key threshold)
   (memoizer f (cache/fifo-cache-factory {} :threshold threshold) base)))

;; ### LRU

(def-deprecated lru
  "DEPRECATED: Please use clojure.core.memoize/lru instead."
  ([f] (memo-lru f 32))
  ([f limit] (memo-lru f limit {}))
  ([f limit base]
   (memoizer f (cache/lru-cache-factory {} :threshold limit) base)))

(defn lru
  "Works the same as the basic memoization function (i.e. `memo`
   and `core.memoize` except when a given threshold is breached.

   Observe the following:

       (require '[clojure.core.memoize :as memo])

       (def id (memo/lru identity :lru/threshold 2))

       (id 42)
       (id 43)
       (snapshot id)
       ;=> {[42] 42, [43] 43}

   At this point the cache has not yet crossed the set threshold
   of `2`, but if you execute yet another call the story will
   change:

       (id 44)
       (snapshot id)
       ;=> {[44] 44, [43] 43}

   At this point the operation of the LRU cache looks exactly
   the same at the FIFO cache.  However, the difference becomes
   apparent on further use:

       (id 43)
       (id 0)
       (snapshot id)
       ;=> {[0] 0, [43] 43}

   As you see, once again calling `id` with the argument `43`
   will expose the LRU nature of the underlying cache.  That is,
   when the threshold is passed, the cache will expel the
   **L**east **R**ecently **U**sed element in favor of the new."
  ([f] (lru f {} :lru/threshold 32))
  ([f base] (lru f base :lru/threshold 32))
  ([f tkey threshold] (lru f {} tkey threshold))
  ([f base key threshold]
   (check-args "lru" f base key threshold)
   (memoizer f (cache/lru-cache-factory {} :threshold threshold) base)))

;; ### TTL

(def-deprecated ttl
  "DEPRECATED: Please use clojure.core.memoize/ttl instead."
  ([f] (memo-ttl f 3000 {}))
  ([f limit] (memo-ttl f limit {}))
  ([f limit base]
   (memoizer f (cache/ttl-cache-factory {} :ttl limit) base)))

(defn ttl
  "Unlike many of the other core.memo memoization functions,
   `memo-ttl`'s cache policy is time-based rather than algorithmic
   or explicit.  When memoizing a function using `memo-ttl` you
   should provide a **T**ime **T**o **L**ive parameter in
   milliseconds.

       (require '[clojure.core.memoize :as memo])

       (def id (memo/ttl identity :ttl/threshold 5000))

       (id 42)
       (snapshot id)
       ;=> {[42] 42}

       ... wait 5 seconds ...
       (id 43)
       (snapshot id)
       ;=> {[43] 43}

   The expired cache entries will be removed on each cache **miss**."
  ([f] (ttl f {} :ttl/threshold 32))
  ([f base] (ttl f base :ttl/threshold 32))
  ([f tkey threshold] (ttl f {} tkey threshold))
  ([f base key threshold]
   (check-args "ttl" f base key threshold)
   (memoizer f (cache/ttl-cache-factory {} :ttl threshold) base)))

;; ### LU

(def-deprecated lu
  "DEPRECATED: Please use clojure.core.memoize/lu instead."
  ([f] (memo-lu f 32))
  ([f limit] (memo-lu f limit {}))
  ([f limit base]
   (memoizer f (cache/lu-cache-factory {} :threshold limit) base)))

(defn lu
  "Similar to the implementation of memo-lru, except that this
   function removes all cache values whose usage value is
   smallest:

       (require '[clojure.core.memoize :as memo])

       (def id (memo/lu identity :lu/threshold 3))

       (id 42)
       (id 42)
       (id 43)
       (id 44)
       (snapshot id)
       ;=> {[44] 44, [42] 42}

   The **L**east **U**sed values are cleared on cache misses."
  ([f] (lu f {} :lu/threshold 32))
  ([f base] (lu f base :lu/threshold 32))
  ([f tkey threshold] (lu f {} tkey threshold))
  ([f base key threshold]
   (check-args "lu" f base key threshold)
   (memoizer f (cache/lu-cache-factory {} :threshold threshold) base)))
