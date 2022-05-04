;; # 🖨 Better Printing
(ns ^:nextjournal.clerk/no-cache viewers.printing
  (:require [clojure.datafy :as datafy]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk :refer [with-viewer]]
            [nextjournal.clerk.viewer :as v]))

;; For reference, let's look at `core_print.clj` (jump to `*print-length*`) and `cider.nrepl` (jump to `cider.nrepl.print-method/def-print-method`).


(clerk/set-viewers! v/default-viewers)

;; ## Compare with the REPL
;; ### Finding `print-method` implementations
(def print-method-impls
  (->> print-method methods keys (mapv (fn [x] (if (class? x) (symbol (.getName x)) x))) set))

(defn print-with-dispatch [dispatch x]
  (let [w (java.io.StringWriter.)
        f (-> print-method methods (get dispatch))]
    (when f
      (f x w)
      (str w))))

(print-with-dispatch :default (atom {}))

(print-with-dispatch clojure.lang.Atom (atom {}))

(print-with-dispatch clojure.lang.IDeref (atom {}))

(def default-print-method-impls
  '#{nil java.lang.Object clojure.lang.ISeq clojure.lang.ReaderConditional java.util.Date java.lang.Throwable java.util.regex.Pattern :default java.lang.StackTraceElement clojure.lang.Var clojure.core.Eduction java.util.Map java.util.UUID java.lang.Character java.sql.Timestamp java.lang.Double java.lang.Boolean :clojure.core/VecSeq java.lang.Float java.lang.String clojure.lang.IPersistentSet java.util.RandomAccess clojure.lang.IDeref java.util.Set java.util.Calendar java.lang.Class clojure.lang.IPersistentMap clojure.lang.IPersistentVector java.lang.Number :clojure.core/Vec java.util.List clojure.lang.BigInt java.math.BigDecimal clojure.lang.Keyword clojure.lang.Symbol clojure.lang.TaggedLiteral clojure.lang.IRecord})

(def print-method-overrides
  (->> (set/difference print-method-impls default-print-method-impls)
       (filter #(str/starts-with? (str %) "clojure.lang"))
       (mapv resolve)))

(doseq [dispatch print-method-overrides]
  (remove-method print-method dispatch))

;; Let's re-evaluate the the `clojure/core_print.clj` default implemetation
(defmethod print-method clojure.lang.IDeref [o w]
  (#'clojure.core/print-tagged-object o (#'clojure.core/deref-as-map o) w))

(print-with-dispatch :default (atom {}))

(print-with-dispatch clojure.lang.Atom (atom {}))

(print-with-dispatch clojure.lang.IDeref (atom {}))


;; ### Clojure's default printing

^{::clerk/visibility :hide}
(with-viewer :read+inspect
  "#object[clojure.lang.Atom 0x1a38ba58 {:status :ready, :val (0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29)}]")

^{::clerk/visibility :hide}
(with-viewer :read+inspect
  "#object[clojure.lang.Namespace 0x1e53135d \"clojure.core\"]")

;; ### `cider-nrepl`

;; Atom
^{::clerk/visibility :hide}
(with-viewer :read+inspect
  "#atom[(0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29) 0x78fc3e4b]")

;; Namespace
^{::clerk/visibility :hide}
(with-viewer :read+inspect
  "#namespace[clojure.core]")

;; Like Clojure's default:
(with-viewer {:pred #(instance? clojure.lang.IDeref %)
              :transform-fn (fn [r] (with-viewer :tagged-value
                                      {:tag "object"
                                       :value (vector (class r)
                                                      (with-viewer :number-hex (System/identityHashCode r))
                                                      (if-let [deref-as-map (resolve 'clojure.core/deref-as-map)]
                                                        (deref-as-map r)
                                                        r))}))}
  (atom {:range (range 100)}))

;; Like Cider
(with-viewer {:pred (partial instance? clojure.lang.IRef)
              :transform-fn (fn [r] (with-viewer :tagged-value
                                      {:tag "atom"
                                       :value [(deref r)
                                               (with-viewer :number-hex (System/identityHashCode r))]}))}
  (atom {:range (range 100)}))

;; ## Possible Solutions
;; ### 1. Print like Clojure's default
;; It's the default and it makes it pretty clear what's going on. It's also nicely uniform and shows the class for things.
;; ### 2. Print like `cider.nrepl`
;; A bit more concise at the expense of hiding certain or de-emphasising information (implementing class and the idenity hash code).
;; ### 3. Detect how `pr-str` would print it and do the same
;; This might have the downside of being confusing to folks when `cider-jack-in` will affect how Clerk displays things but otoh that is also true of `pr-str` today.

;; ## Testcases

(atom (vec (range 100)))

(ref (vec (range 100)))

(java.io.File. "/Users/mk/dev/blog")

inc

#'inc

(datafy/datafy #'inc)

(find-ns 'nextjournal.clerk)

(datafy/datafy (find-ns 'nextjournal.clerk))

(defmulti foo :bar)

(delay :foo)

(future :foo)

;; ## Rejected Alternative: Datafy

;; Initially I thought we'd use `clojure.core/datafy` in case there's a non-trivial implementation for a given object.
;; But I rejected this because the default implementations for `class` and `namespace` would be confusing.
(type (atom {}))

(datafy/datafy (type (atom {})))

