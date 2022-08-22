;; # 🖨 Better Printing ADR
^{:nextjournal.clerk/visibility {:code :hide}}
(ns ^:nextjournal.clerk/no-cache viewers.printing
  (:require [clojure.datafy :as datafy]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk :refer [with-viewer]]
            [nextjournal.clerk.viewer :as v]))


(clerk/reset-viewers! v/default-viewers)

;; ## Compare with the REPL

;; For reference, let's look at `core_print.clj` (jump to `*print-length*`) and `cider.nrepl` (jump to `cider.nrepl.print-method/def-print-method`).
;; Let's re-evaluate the the `clojure/core_print.clj` [default implemetation](https://github.com/clojure/clojure/blob/35bd89f05f8dc4aec47001ca10fe9163abc02ea6/src/clj/clojure/core_print.clj#L459-L460) (but with a dispatch value of `clojure.lang.Atom`).
(defmethod print-method clojure.lang.Atom [o w]
  (#'clojure.core/print-tagged-object o (#'clojure.core/deref-as-map o) w))

(with-viewer :read+inspect
  (pr-str (atom {})))

;; This is [cider-nrepl's implementation](https://github.com/clojure-emacs/cider-nrepl/blob/5c0f21197fcccb1b2ca67054cab1dcc8a6af2c7f/src/cider/nrepl/print_method.clj#L37-L40).
(defmethod print-method clojure.lang.Atom [o w]
  (.write w "#atom[")
  (.write w (pr-str @o))
  (.write w (format " 0x%x]" (System/identityHashCode o))))

(with-viewer :read+inspect
  (pr-str (atom {})))

;; ### Viewer Implementations

;; Like Clojure's default:
(with-viewer {:pred #(instance? clojure.lang.IDeref %)
              :transform-fn (fn [wrapped-value] (with-viewer :tagged-value
                                                  {:tag "object"
                                                   :value (let [r (v/->value wrapped-value)]
                                                            (vector (type r)
                                                                    #?(:clj (with-viewer :number-hex (System/identityHashCode r)))
                                                                    (if-let [deref-as-map (resolve 'clojure.core/deref-as-map)]
                                                                      (deref-as-map r)
                                                                      r)))}))}
  (atom {:range (range 100)}))

;; Like Cider
(with-viewer {:pred (partial instance? clojure.lang.IRef)
              :transform-fn (fn [wrapped-value] (with-viewer :tagged-value
                                                  {:tag "atom"
                                                   :value [(deref (v/->value wrapped-value))
                                                           (with-viewer :number-hex (System/identityHashCode (v/->value wrapped-value)))]}))}
  (atom {:range (range 100)}))

;; ## Possible Solutions
;; ### 1. Print like Clojure's default
;; It's the default and it makes it pretty clear what's going on. It's also nicely uniform and shows the class for things.
;; ### 2. Print like `cider.nrepl`
;; A bit more concise at the expense of hiding certain or de-emphasising information (implementing class and the idenity hash code).
;; ### 3. Detect how `pr-str` would print it and do the same
;; This might have the downside of being confusing to folks when `cider-jack-in` will affect how Clerk displays things but otoh that is also true of `pr-str` today.

;; ## Rejected Alternative: Datafy

;; Initially I thought we'd use `clojure.core/datafy` in case there's a non-trivial implementation for a given object.
;; But I rejected this because the default implementations for `class` and `namespace` would be confusing.
(type (atom {}))

(datafy/datafy (type (atom {})))

;; ## Decision

;; I decided to implement a custom viewer for `clojure.lang.IDeref` that looks like what `clojure.core` does, but performs JVM-side pagination. For other custom classes not handled by Clerk's default viewers, we call `pr-str` on the JVM and apply the `:read+inspect` viewer. This viewer does in-process pagination in the browser but I hope this is rarely needed since the strings it's given should be small. We've also been able to drop the custom viewers for `inst?` and `fn?` since they are handled well by this approach as well. This has the upside of letting Clerk show custom classes identital to what folks would see on the REPL. This means that bringing in a dependency like `cider.nrepl` affects how Clerk will display things which one could consider to be both good (for example `java.time` related stuff like look right) and bad (`cider-jack-in` brings in the middleware and affects how Clerk shows stuff).
;;
;; We're thinking about working around this and consistently show what Clojure does by default in a future release.

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

(java.util.Date.)

(java.time.Instant/now)

(java.time.LocalTime/now)

(java.time.LocalDateTime/now)

(with-viewer :read+inspect
  (pr-str {:range (range 100 200)}))

(comp inc dec)

(re-pattern "hel?o")

(java.util.UUID/randomUUID)
