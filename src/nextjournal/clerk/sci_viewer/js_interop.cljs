(ns nextjournal.clerk.sci-viewer.js-interop
  (:refer-clojure :exclude [let fn defn spread])
  (:require
   [applied-science.js-interop :as j]
   [applied-science.js-interop.destructure :as d]
   [clojure.core :as c]
   [sci.core :as sci]))

(def jns (sci/create-ns 'applied-science.js-interop nil))

(c/defn ^:macro let
  "`let` with destructuring that supports js property and array access.
   Use ^:js metadata on the binding form to invoke. Eg/
   (let [^:js {:keys [a]} obj] …)"
  [_ _ bindings & body]
  (if (empty? bindings)
    `(do ~@body)
    `(~'clojure.core/let ~(vec (d/destructure (take 2 bindings)))
      (~'applied-science.js-interop/let
       ~(vec (drop 2 bindings))
       ~@body))))

(c/defn ^:macro fn
  "`fn` with argument destructuring that supports js property and array access.
   Use ^:js metadata on binding forms to invoke. Eg/
   (fn [^:js {:keys [a]}] …)"
  [_ _ & args]
  (cons 'clojure.core/fn (d/destructure-fn-args args)))

(c/defn ^:macro defn
  "`defn` with argument destructuring that supports js property and array access.
   Use ^:js metadata on binding forms to invoke."
  [_ _ & args]
  (cons 'clojure.core/defn (d/destructure-fn-args args)))

(c/defn litval* [v]
  (if (keyword? v)
    (cond->> (name v)
      (namespace v)
      (str (namespace v) "/"))
    v))

(declare lit*)

(defn- spread
  "For ~@spread values, returns the unwrapped value,
   otherwise returns nil."
  [x]
  (when (and (seq? x)
             (= 'clojure.core/unquote-splicing (first x)))
    (second x)))

(defn- tagged-sym [tag] (with-meta (gensym (name tag)) {:tag tag}))

(c/defn lit*
  "Recursively converts literal Clojure maps/vectors into JavaScript object/array expressions
  Options map accepts a :keyfn for custom key conversions."
  ([x]
   (lit* nil x))
  ([{:as opts
     :keys [keyfn valfn _env]
     :or {keyfn identity
          valfn litval*}} x]
   (cond (map? x)
         (list* 'applied-science.js-interop/obj
                (reduce-kv #(conj %1 (keyfn %2) (lit* opts %3)) [] x))
         (vector? x)
         (if (some spread x)
           (c/let [sym (tagged-sym 'js/Array)]
             `(c/let [~sym (~'cljs.core/array)]
                ;; handling the spread operator
                ~@(for [x'
                        ;; chunk array members into spreads & non-spreads,
                        ;; so that sequential non-spreads can be lumped into
                        ;; a single .push
                        (->> (partition-by spread x)
                             (mapcat (clojure.core/fn [x]
                                       (if (spread (first x))
                                         x
                                         (list x)))))]
                    (if-let [x' (spread x')]
                      (if false
                        ;; for now disable this optimization
                        #_(and env (inf/tag-in? env '#{array} x'))
                        `(.forEach ~x' (c/fn [x#] (.push ~sym x#)))
                        `(doseq [x# ~(lit* x')] (.push ~sym x#)))
                      `(.push ~sym ~@(map lit* x'))))
                ~sym))
           (list* 'cljs.core/array (mapv lit* x)))
         :else (valfn x))))

(c/defn ^:macro lit
  "Recursively converts literal Clojure maps/vectors into JavaScript object/array expressions
   (using j/obj and cljs.core/array)"
  [_ &env form]
  (lit* {:env &env} form))

(def js-interop-namespace
  {'get (sci/copy-var j/get jns)
   'get-in (sci/copy-var j/get-in jns)
   'contains? (sci/copy-var j/contains? jns)
   'select-keys (sci/copy-var j/select-keys jns)
   'lookup (sci/copy-var j/lookup jns)
   'assoc! (sci/copy-var j/assoc! jns)
   'assoc-in! (sci/copy-var j/assoc-in! jns)
   'update! (sci/copy-var j/update! jns)
   'update-in! (sci/copy-var j/update-in! jns)
   'extend! (sci/copy-var j/extend! jns)
   'push! (sci/copy-var j/push! jns)
   'unshift! (sci/copy-var j/unshift! jns)
   'call (sci/copy-var j/call jns)
   'apply (sci/copy-var j/apply jns)
   'call-in (sci/copy-var j/call-in jns)
   'apply-in (sci/copy-var j/apply-in jns)
   'obj (sci/copy-var j/obj jns)
   'let (sci/copy-var let jns)
   'fn (sci/copy-var fn jns)
   'defn (sci/copy-var defn jns)
   'lit (sci/copy-var lit jns)})


