;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.jvm.utils
  (:require [clojure.tools.analyzer.utils :as u]
            [clojure.tools.analyzer.env :as env]
            [clojure.reflect :as reflect]
            [clojure.string :as s]
            [nextjournal.clerk.clojure.core.memoize :refer [lru]]
            [clojure.java.io :as io])
  (:import (clojure.lang RT Symbol Var)
           org.objectweb.asm.Type))

(set! *warn-on-reflection* true)

(defn ^:private type-reflect
  [typeref & options]
  (apply reflect/type-reflect typeref
         :reflector (reflect/->JavaReflector (RT/baseLoader))
         options))

(defn macro? [sym env]
  (when-let [v (u/resolve-sym sym env)]
    (and (not (-> env :locals (get sym)))
         (u/macro? v)
         v)))

(defn inline? [sym args env]
  (when-let [v (u/resolve-sym sym env)]
    (let [inline-arities-f (:inline-arities (meta v))]
      (and (not (-> env :locals (get sym)))
           (or (not inline-arities-f)
               (inline-arities-f (count args)))
           (:inline (meta v))))))

(defn specials [c]
  (case c
    "byte" Byte/TYPE
    "boolean" Boolean/TYPE
    "char" Character/TYPE
    "int" Integer/TYPE
    "long" Long/TYPE
    "float" Float/TYPE
    "double" Double/TYPE
    "short" Short/TYPE
    "void" Void/TYPE
    "object" Object
    nil))

(defn special-arrays [c]
  (case c
    "bytes" (Class/forName "[B")
    "booleans" (Class/forName "[Z")
    "chars" (Class/forName "[C")
    "ints" (Class/forName "[I")
    "longs" (Class/forName "[J")
    "floats" (Class/forName "[F")
    "doubles" (Class/forName "[D")
    "shorts" (Class/forName "[S")
    "objects" (Class/forName "[Ljava.lang.Object;")
    nil))

(defmulti ^Class maybe-class
  "Takes a Symbol, String or Class and tires to resolve to a matching Class"
  class)

(defn array-class
  ([element-type] (array-class 1 element-type))
  ([n element-type]
   (RT/classForName
    (str (apply str (repeat n"["))
         (-> element-type
             maybe-class
             Type/getType
             .getDescriptor
             (.replace \/ \.))))))

(defn maybe-class-from-string [^String s]
  (or (when-let [maybe-class (and (neg? (.indexOf s "."))
                                  (not= \[ (first s))
                                  (if env/*env*
                                    (u/resolve-sym (symbol s) {:ns (ns-name *ns*)})
                                    ((ns-map *ns*) (symbol s))))]
        (when (class? maybe-class) maybe-class))
      (try (RT/classForName s)
           (catch ClassNotFoundException _))))

(defmethod maybe-class :default [_] nil)
(defmethod maybe-class Class [c] c)
(defmethod maybe-class String [s]
  (maybe-class (symbol s)))

(defn maybe-array-class-sym [x]
  (let [sname (name x)]
    (if-let [c (and (= (count sname) 1)
                    (Character/isDigit (char (first sname)))
                    (namespace x))]
      (when-let [c (or (specials c)
                       (maybe-class-from-string c))]
        (array-class (Integer/parseInt sname) c)))))

(defmethod maybe-class Symbol [sym]
  (let [sname (name sym)
        snamec (count sname)]
    (or (maybe-array-class-sym sym)
        (when-not (namespace sym)
          (if-let [base-type (and (.endsWith sname "<>")
                                  (maybe-class (subs sname 0 (- snamec 2))))]
            ;; TODO: we're leaking into the syntax
            (array-class base-type)
            (if-let [ret (or (specials sname)
                             (special-arrays sname))]
              ret
              (maybe-class-from-string sname)))))))

(defn maybe-class-literal [x]
  (cond
    (class? x) x
    (symbol? x) (or (maybe-array-class-sym x)
                    (and (not (namespace x))
                         (maybe-class-from-string (name x))))
    (string? x) (maybe-class-from-string x)))

(def primitive?
  "Returns non-nil if the argument represents a primitive Class other than Void"
  #{Double/TYPE Character/TYPE Byte/TYPE Boolean/TYPE
    Short/TYPE Float/TYPE Long/TYPE Integer/TYPE})

(def ^:private convertible-primitives
  "If the argument is a primitive Class, returns a set of Classes
   to which the primitive Class can be casted"
  {Integer/TYPE   #{Integer Long/TYPE Long Short/TYPE Byte/TYPE Object Number}
   Float/TYPE     #{Float Double/TYPE Object Number}
   Double/TYPE    #{Double Float/TYPE Object Number}
   Long/TYPE      #{Long Integer/TYPE Short/TYPE Byte/TYPE Object Number}
   Character/TYPE #{Character Object}
   Short/TYPE     #{Short Object Number}
   Byte/TYPE      #{Byte Object Number}
   Boolean/TYPE   #{Boolean Object}
   Void/TYPE      #{Void}})

(defn ^Class box
  "If the argument is a primitive Class, returns its boxed equivalent,
   otherwise returns the argument"
  [c]
  ({Integer/TYPE   Integer
    Float/TYPE     Float
    Double/TYPE    Double
    Long/TYPE      Long
    Character/TYPE Character
    Short/TYPE     Short
    Byte/TYPE      Byte
    Boolean/TYPE   Boolean
    Void/TYPE      Void}
   c c))

(defn ^Class unbox
  "If the argument is a Class with a primitive equivalent, returns that,
   otherwise returns the argument"
  [c]
  ({Integer   Integer/TYPE,
    Long      Long/TYPE,
    Float     Float/TYPE,
    Short     Short/TYPE,
    Boolean   Boolean/TYPE,
    Byte      Byte/TYPE,
    Character Character/TYPE,
    Double    Double/TYPE,
    Void      Void/TYPE}
   c c))

(defn numeric?
  "Returns true if the given class is numeric"
  [c]
  (when c
    (.isAssignableFrom Number (box c))))

(defn subsumes?
  "Returns true if c2 is subsumed by c1"
  [c1 c2]
  (let [c1 (maybe-class c1)
        c2 (maybe-class c2)]
    (and (not= c1 c2)
         (or (and (not (primitive? c1))
                  (primitive? c2))
             (.isAssignableFrom c2 c1)))))

(defn convertible?
  "Returns true if it's possible to convert from c1 to c2"
  [c1 c2]
  (let [c1 (maybe-class c1)
        c2 (maybe-class c2)]
    (if (nil? c1)
      (not (primitive? c2))
      (or
       (= c1 c2)
       (.isAssignableFrom c2 c1)
       (and (primitive? c2)
            ((convertible-primitives c2) c1))
       (and (primitive? c1)
            (.isAssignableFrom (box c1) c2))))))

(def wider-than
  "If the argument is a numeric primitive Class, returns a set of primitive Classes
   that are narrower than the given one"
  {Long/TYPE    #{Integer/TYPE Short/TYPE Byte/TYPE}
   Integer/TYPE #{Short/TYPE Byte/TYPE}
   Float/TYPE   #{Integer/TYPE Short/TYPE Byte/TYPE Long/TYPE}
   Double/TYPE  #{Integer/TYPE Short/TYPE Byte/TYPE Long/TYPE Float/TYPE}
   Short/TYPE   #{Byte/TYPE}
   Byte/TYPE    #{}})

(defn wider-primitive
  "Given two numeric primitive Classes, returns the wider one"
  [from to]
  (if ((wider-than from) to)
    from
    to))

(defn wider-tag*
  "Given two Classes returns the wider one"
  [from to]
  (if (not= from to)
    (if (primitive? from)
      (if (primitive? to)
        (wider-primitive from to)
        (or (and (numeric? from)
                 (numeric? to)
                 to)
            ((convertible-primitives from) to)))
      (if (primitive? to)
        (or (and (numeric? from)
                 (numeric? to)
                 from)
            ((convertible-primitives to) from))
        (if (convertible? from to)
          to
          (when (convertible? to from)
            from))))
    from))

(defn wider-tag
  "Given a collection of Classes returns the wider one"
  [tags]
  (let [tags* (filter identity tags)
        wider (loop [wider (first tags*) tags* (rest tags*)]
                (if (seq tags*)
                  (if-let [t (wider-tag* wider (first tags*))]
                    (recur t (rest tags*)))
                  wider))]
    (when (or (= tags* tags)
              (not (primitive? wider)))
      wider)))

(defn name-matches?
  [member]
  (let [member-name (str member)
        i (.lastIndexOf member-name ".")
        member-name* (when (pos? i)
                       (str (s/replace (subs member-name 0 i) "-" "_") (subs member-name i)))
        member-name** (s/replace member-name "-" "_")
        member-name*** (munge member-name)]
    (fn [name]
      (let [name (str name)]
        (or (= member-name name)
            (= member-name* name)
            (= member-name** name)
            (= member-name*** name))))))

(def object-members
  (:members (type-reflect Object)))

(def members*
  (lru (fn members*
         ([class]
          (into object-members
                (remove (fn [{:keys [flags]}]
                          (not-any? #{:public :protected} flags))
                        (-> class
                            maybe-class
                            ^Class (box)
                            .getName
                            symbol
                            (type-reflect :ancestors true)
                            :members)))))))

(defn members
  ([class] (members* class))
  ([class member]
     (when-let [members (filter #((name-matches? member) (:name %))
                                (members* class))]
       members)))

(defn static-members [class f]
  (when-let [members (members class f)]
    (when-let [statics (filter (comp :static :flags) members)]
      statics)))

(defn instance-members [class f]
  (when-let [members (members class f)]
    (when-let [i-members (remove (comp :static :flags) members)]
      i-members)))

(defn static-methods [class method argc]
  (filter #(= argc (count (:parameter-types %)))
          (filter :return-type (static-members class method))))

(defn instance-methods [class method argc]
  (filter #(= argc (count (:parameter-types %)))
          (filter :return-type (instance-members class method))))

(defn static-field [class f]
  (when-let [statics (static-members class f)]
    (when-let [[member] (filter (every-pred (comp nil? seq :parameter-types)
                                            (comp nil? :return-type))
                                statics)]
      member)))

(defn instance-field [class f]
  (when-let [i-members (instance-members class f)]
    (when-let [[member] (filter (every-pred (comp nil? seq :parameter-types)
                                            (comp nil? :return-type))
                                i-members)]
      member)))

(defn static-method [class method]
  (first (static-methods class method 0)))

(defn instance-method [class method]
  (first (instance-methods class method 0)))

(defn prim-or-obj
  "If the given Class is a primitive, returns that Class, otherwise returns Object"
  [tag]
  (if (and tag (primitive? tag))
    tag
    java.lang.Object))

(defn prim-interface [tags]
  (when (some primitive? tags)
    (let [sig (apply str (mapv #(.toUpperCase (subs (.getSimpleName ^Class %) 0 1)) tags))]
      (maybe-class (str "clojure.lang.IFn$" sig)))))

(defn tag-match? [arg-tags meth]
  (every? identity (map convertible? arg-tags (:parameter-types meth))))

(defn try-best-match
  "Given a vector of arg tags and a collection of methods, tries to return the
   subset of methods that match best the given tags"
  [tags methods]
  (let [o-tags (mapv #(or (maybe-class %) Object) tags)]
    (if-let [methods (or (seq (filter
                               #(= o-tags (mapv maybe-class (:parameter-types %))) methods))
                         (seq (filter #(tag-match? tags %) methods)))]
      (reduce (fn [[prev & _ :as p] next]
                (let [prev-params (mapv maybe-class (:parameter-types prev))
                      next-params (mapv maybe-class (:parameter-types next))
                      prev-ret    (maybe-class (:return-type prev))
                      next-ret    (maybe-class (:return-type next))
                      prev-decl   (maybe-class (:declaring-class prev))
                      next-decl   (maybe-class (:declaring-class next))]
                  (cond
                   (not prev)
                   [next]
                   (= prev-params next-params)
                   (cond
                    (= prev-ret next-ret)
                    (cond
                     (.isAssignableFrom prev-decl next-decl)
                     [next]
                     (.isAssignableFrom next-decl prev-decl)
                     p
                     :else
                     (conj p next))
                    (.isAssignableFrom prev-ret next-ret)
                    [next]
                    (.isAssignableFrom next-ret prev-ret)
                    p
                    :else
                    (conj p next))
                   (and (some true? (map subsumes? next-params prev-params))
                        (not-any? true? (map subsumes? prev-params next-params)))
                   [next]
                   :else
                   (conj p next)))) [] methods)
      methods)))

(defn ns->relpath [s]
  (-> s str (s/replace \. \/) (s/replace \- \_) (str ".clj")))

(defn ns-url [ns]
  (let [f (ns->relpath ns)]
    (or (io/resource f)
        (io/resource (str f "c")))))
