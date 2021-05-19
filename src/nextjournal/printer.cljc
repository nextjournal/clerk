(ns nextjournal.printer
  "This namespace overrides some standard printing behavior.

  Instead of a simple ellipsis whenever the `*print-length*` is exhausted,
  we wrap the printed sequence in metadata that contains
  `:nextjournal/truncated?` set to true.

  For example (1 2 3 ...) becomes ^{:nextjournal/truncated?} (1 2 3).
  Other sequence types and maps behave similarly.

  Whenever `*print-level*` is exhausted the printer prints a tag/value pair
  of #nextjournal/empty nil instead of a simple hash sign #, so that
  the sequence can still be read by an EDN reader.

  Note that the printer does not modify the behavior of the standard printer
  with respect to `*print-dup*` or `*print-readably*`."
  (:refer-clojure :exclude [pr pr-on pr-str])
  (:require [nextjournal.datafy-nav :as df])
  (:import (java.io Writer)))

;; This namespace is mainly copied from core_print.clj and some parts from
;; core.clj itself.

#?(:bb (do
         (def ^:dynamic *print-readably* true)
         (def ^:dynamic *print-dup* false)))

(defn- print-sequential [^String begin, print-one, ^String sep, ^String end, sequence, ^Writer w]
  (binding [*print-level* (and (not *print-dup*) *print-level* (dec *print-level*))]
    (if (and *print-level* (neg? *print-level*))
      ;; see the documentation of this namespace
      (.write w "#nextjournal/empty nil")
      (do
        (.write w begin)
        (when-let [xs (seq sequence)]
          (if (and (not *print-dup*) *print-length*)
            (loop [[x & xs] xs
                   print-length *print-length*]
              (when-not (zero? print-length)
                (print-one x w)
                (when (and xs (pos? (dec print-length)))
                  (.write w sep)
                  (recur xs (dec print-length)))))
            (loop [[x & xs] xs]
              (print-one x w)
              (when xs
                (.write w sep)
                (recur xs)))))
        (.write w end)))))

(defmulti print-nj (fn [x writer]
                      (let [t (get (meta x) :type)]
                        (if (keyword? t) t (class x)))))

(defmethod print-nj :default [x w]
  (print-method x w))

(defn pr-on
  {:private true
   :static true}
  [x w]
  (if *print-dup*
    (print-dup x w)
    (print-nj x w))
  nil)

(defn pr
  "Prints the object(s) to the output stream that is the current value
  of *out*.  Prints the object(s), separated by spaces if there is
  more than one.  By default, pr and prn print in a way that objects
  can be read by the reader"
  ([] nil)
  ([x]
   (pr-on x *out*))
  ([x & more]
   (pr x)
   (. *out* (append \space))
   (if-let [nmore (next more)]
     (recur (first more) nmore)
     (apply pr more))))

(defn pr-str
  "pr to a string, returning it"
  [& xs]
  (with-out-str
    (apply pr xs)))

(defn- print-meta [o, ^Writer w]
  (when-let [m (meta o)]
    (when (and (pos? (count m))
               (or *print-dup*
                   (and *print-meta* *print-readably*)))
      (.write w "^")
      (if (and (= (count m) 1) (:tag m))
        (pr-on (:tag m) w)
        (pr-on m w))
      (.write w " "))))

(defn will-be-truncated? [c]
  (and *print-length* (< *print-length* (bounded-count (inc *print-length*) (seq c)))))

(defn- wrap-seq-with-truncated [x]
  (cond-> x
    (will-be-truncated? x)
    (vary-meta assoc :nextjournal/truncated? true)))

;; vectors
(defmethod print-nj clojure.lang.IPersistentVector [v, ^Writer w]
  (let [v (wrap-seq-with-truncated v)]
    (print-meta v w)
    (print-sequential "[" pr-on " " "]" v w)))

;; sequence types
(defmethod print-nj clojure.lang.ISeq [o, ^Writer w]
  (let [o (wrap-seq-with-truncated o)]
    (print-meta o w)
    (print-sequential "(" pr-on " " ")" o w)))

(prefer-method print-nj clojure.lang.ISeq clojure.lang.IPersistentCollection)
#?(:bb nil
   :clj (prefer-method print-nj clojure.lang.ISeq java.util.Collection))

;; maps
(defn- print-prefix-map [prefix m print-one w]
  (print-sequential
   (str prefix "{")
   (fn [e ^Writer w]
     (print-one (key e) w) (.append w \space) (print-one (val e) w))
   ", "
   "}"
   (seq m) w))

(defn- print-map [m print-one w]
  (print-prefix-map nil m print-one w))

(defn- strip-ns
  [named]
  (if (symbol? named)
    (symbol nil (name named))
    (keyword nil (name named))))

(defn- lift-ns
  "Returns [lifted-ns lifted-map] or nil if m can't be lifted."
  [m]
  (when *print-namespace-maps*
    (loop [ns nil
           [[k v :as entry] & entries] (seq m)
           lm {}]
      (if entry
        (when (or (keyword? k) (symbol? k))
          (if ns
            (when (= ns (namespace k))
              (recur ns entries (assoc lm (strip-ns k) v)))
            (when-let [new-ns (namespace k)]
              (recur new-ns entries (assoc lm (strip-ns k) v)))))
        [ns (apply conj (empty m) lm)]))))

(defmethod print-nj clojure.lang.IPersistentMap [m, ^Writer w]
  (let [m (wrap-seq-with-truncated m)]
    (print-meta m w)
    (let [[ns lift-map] (lift-ns m)]
      (if ns
        (print-prefix-map (str "#:" ns) lift-map pr-on w)
        (print-map m pr-on w)))))

;; sets
(defmethod print-nj clojure.lang.IPersistentSet [s, ^Writer w]
  (let [s (wrap-seq-with-truncated s)]
    (print-meta s w)
    (print-sequential "#{" pr-on " " "}" (seq s) w)))

;; vars
(defmethod print-nj #?(:bb sci.lang.IVar :clj clojure.lang.Var) [x ^Writer w]
  (.write w (pr-str ^{:nextjournal/tag 'var}
                    [(str (symbol x)) (-> x var-get df/safe-datafy)])))

#?(:bb nil
   :clj (do
          ;; object printing
          (defn- print-tagged-object [o rep ^Writer w]
            (when (instance? clojure.lang.IMeta o)
              (print-meta o w))
            (.write w "#object[")
            (let [c (class o)]
              (if (.isArray c)
                (print-nj (.getName c) w)
                (.write w (.getName c))))
            (.write w " ")
            (.write w (format "0x%x " (System/identityHashCode o)))
            (print-nj rep w)
            (.write w "]"))

          (defn- print-object [o, ^Writer w]
            (print-tagged-object o (str o) w))

          ;; java.util
          (prefer-method print-nj clojure.lang.IPersistentCollection java.util.Collection)
          (prefer-method print-nj clojure.lang.IPersistentCollection java.util.RandomAccess)
          (prefer-method print-nj java.util.RandomAccess java.util.List)
          (prefer-method print-nj clojure.lang.IPersistentCollection java.util.Map)

          (defn- print-meta*
            "Prints the given metadata directly."
            [m, ^Writer w]
            (when (and m
                       (pos? (count m))
                       (or *print-dup*
                           (and *print-meta* *print-readably*)))
              (.write w "^")
              (if (and (= (count m) 1) (:tag m))
                (pr-on (:tag m) w)
                (pr-on m w))
              (.write w " ")))

          (def truncated-map {:nextjournal/truncated? true})

          (defmethod print-nj java.util.List [c, ^Writer w]
            (if *print-readably*
              (do
                (when (will-be-truncated? c)
                  (print-meta* truncated-map w))
                (print-sequential "(" pr-on " " ")" c w))
              (print-object c w)))

          (defmethod print-nj java.util.RandomAccess [v, ^Writer w]
            (if *print-readably*
              (do
                (when (will-be-truncated? v)
                  (print-meta* truncated-map w))
                (print-sequential "[" pr-on " " "]" v w))
              (print-object v w)))

          (defmethod print-nj java.util.Map [m, ^Writer w]
            (if *print-readably*
              (do
                (when (will-be-truncated? m)
                  (print-meta* truncated-map w))
                (print-map m pr-on w))
              (print-object m w)))

          (defmethod print-nj java.util.Set [s, ^Writer w]
            (if *print-readably*
              (do
                (when (will-be-truncated? s)
                  (print-meta* truncated-map w))
                (print-sequential "#{" pr-on " " "}" (seq s) w))
              (print-object s w)))))

;; Records

(defmethod print-nj clojure.lang.IRecord [r, ^Writer w]
  (let [r (wrap-seq-with-truncated r)]
    (print-meta r w)
    (.write w "#")
    (.write w (.getName (class r)))
    (print-map r pr-on w)))

#?(:bb nil
   :clj (prefer-method print-nj clojure.lang.IRecord java.util.Map))
(prefer-method print-nj clojure.lang.IRecord clojure.lang.IPersistentMap)
