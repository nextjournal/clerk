;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.jvm
  "Analyzer for clojure code, extends tools.analyzer with JVM specific passes/forms"
  (:refer-clojure :exclude [macroexpand-1 macroexpand])
  (:require [clojure.tools.analyzer
             :as ana
             :refer [analyze analyze-in-env wrapping-meta analyze-fn-method]
             :rename {analyze -analyze}]

            [clojure.tools.analyzer
             [utils :refer [ctx resolve-sym -source-info resolve-ns obj? dissoc-env butlast+last mmerge]]
             [ast :refer [walk prewalk postwalk] :as ast]
             [env :as env :refer [*env*]]
             [passes :refer [schedule]]]

            [nextjournal.clerk.clojure.tools.analyzer.jvm.utils :refer :all :as u :exclude [box specials]]

            [clojure.tools.analyzer.passes
             [source-info :refer [source-info]]
             [trim :refer [trim]]
             [elide-meta :refer [elide-meta elides]]
             [warn-earmuff :refer [warn-earmuff]]
             [uniquify :refer [uniquify-locals]]]

            [nextjournal.clerk.clojure.tools.analyzer.passes.jvm
             [analyze-host-expr :refer [analyze-host-expr]]
             [box :refer [box]]
             [constant-lifter :refer [constant-lift]]
             [classify-invoke :refer [classify-invoke]]
             [validate :refer [validate]]
             [infer-tag :refer [infer-tag]]
             [validate-loop-locals :refer [validate-loop-locals]]
             [warn-on-reflection :refer [warn-on-reflection]]
             [emit-form :refer [emit-form]]]

            [clojure.java.io :as io]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as readers]

            [nextjournal.clerk.clojure.core.memoize :refer [memo-clear!]])
  (:import (clojure.lang IObj RT Compiler Var)
           java.net.URL))

(set! *warn-on-reflection* true)

(def ns-safe-macro
  "Clojure macros that are known to not alter namespaces"
  #{#'clojure.core/->
    #'clojure.core/->>
    #'clojure.core/..
    #'clojure.core/and
    #'clojure.core/as->
    #'clojure.core/assert
    #'clojure.core/case
    #'clojure.core/cond
    #'clojure.core/cond->
    #'clojure.core/cond->>
    #'clojure.core/condp
    #'clojure.core/defn
    #'clojure.core/defn-
    #'clojure.core/delay
    #'clojure.core/doseq
    #'clojure.core/dosync
    #'clojure.core/dotimes
    #'clojure.core/doto
    #'clojure.core/fn
    #'clojure.core/for
    #'clojure.core/future
    #'clojure.core/if-let
    #'clojure.core/if-not
    #'clojure.core/lazy-seq
    #'clojure.core/let
    #'clojure.core/letfn
    #'clojure.core/loop
    #'clojure.core/or
    #'clojure.core/reify
    #'clojure.core/some->
    #'clojure.core/some->>
    #'clojure.core/sync
    #'clojure.core/time
    #'clojure.core/when
    #'clojure.core/when-let
    #'clojure.core/when-not
    #'clojure.core/while
    #'clojure.core/with-open
    #'clojure.core/with-out-str
    })

(def specials
  "Set of the special forms for clojure in the JVM"
  (into ana/specials
        '#{monitor-enter monitor-exit clojure.core/import* reify* deftype* case*}))

(defn build-ns-map []
  (into {} (mapv #(vector (ns-name %)
                          {:mappings (merge (ns-map %) {'in-ns #'clojure.core/in-ns
                                                        'ns    #'clojure.core/ns})
                           :aliases  (reduce-kv (fn [a k v] (assoc a k (ns-name v)))
                                                {} (ns-aliases %))
                           :ns       (ns-name %)})
                 (all-ns))))

(defn update-ns-map! []
  ((get (env/deref-env) :update-ns-map! #())))

(defn global-env []
  (atom {:namespaces     (build-ns-map)

         :update-ns-map! (fn update-ns-map! []
                           (swap! *env* assoc-in [:namespaces] (build-ns-map)))}))

(defn empty-env
  "Returns an empty env map"
  []
  {:context    :ctx/expr
   :locals     {}
   :ns         (ns-name *ns*)})

(defn desugar-symbol [form env]
  (let [sym-ns (namespace form)]
    (if-let [target (and sym-ns
                         (not (resolve-ns (symbol sym-ns) env))
                         (maybe-class-literal sym-ns))]          ;; Class/field
      (let [opname (name form)
            opname-sym (symbol opname)]
        (if (and (= (count opname) 1)
                 (Character/isDigit ^Character (first opname)))
          form ;; Array/<n>
          (cond
            (= "new" opname)
            `(fn
               ([x#] (new ~(symbol sym-ns) x# d))
               ;; TODO: analyze method and return properly expanded fn
               )
            (or (.startsWith opname ".")
                (let [members (u/members target)]
                  ;; TODO: only pick non-methods!
                  (some #(when (and (= opname-sym (:name %))
                                    (not (instance? clojure.reflect.Field %)))
                           %) members)))
            `(fn
               ([x#] (~form x#))
               ;; TODO: analyze method and return properly expanded fn
               )
            :else
            (with-meta (list '. target (symbol (str "-" opname))) ;; transform to (. Class -field)
              (meta form)))))
      form)))

(defn desugar-host-expr [form env]
  (let [[op & expr] form]
    (if (symbol? op)
      (let [opname (name op)
            opns   (namespace op)
            opns-class ^Class (maybe-class-literal opns)]
        (if-let [target (and opns
                             (not (resolve-ns (symbol opns) env))
                             (when-not (.startsWith opname ".")
                               opns-class))] ; (class/field ..)
          (let [op (symbol opname)]
            (if (= 'new op)
              (with-meta (list* 'new (symbol opns) expr)
                (meta form))
              (with-meta (list '. target (if (zero? (count expr))
                                           op
                                           (list* op expr)))
                (meta form))))

          (cond
           (.startsWith opname ".")     ; (.foo bar ..)
           (let [[target & args] expr
                 target (if opns-class
                          (with-meta (list 'do target)
                            {:tag (symbol (.getName opns-class))})
                          (if-let [target (maybe-class-literal target)]
                            (with-meta (list 'do target)
                              {:tag 'java.lang.Class})
                            target))
                 args (list* (symbol (subs opname 1)) args)]
             (with-meta (list '. target (if (= 1 (count args)) ;; we don't know if (.foo bar) is
                                          (first args) args))  ;; a method call or a field access
               (meta form)))

           (.endsWith opname ".") ;; (class. ..)
           (with-meta (list* 'new (symbol (subs opname 0 (dec (count opname)))) expr)
             (meta form))
           :else form)))
      form)))

(defn macroexpand-1
  "If form represents a macro form or an inlineable function,returns its expansion,
   else returns form."
  ([form] (macroexpand-1 form (empty-env)))
  ([form env]
     (env/ensure (global-env)
       (cond

        (seq? form)
        (let [[op & args] form]
          (if (specials op)
            form
            (let [v (resolve-sym op env)
                  m (meta v)
                  local? (-> env :locals (get op))
                  macro? (and (not local?) (:macro m)) ;; locals shadow macros
                  inline-arities-f (:inline-arities m)
                  inline? (and (not local?)
                               (or (not inline-arities-f)
                                   (inline-arities-f (count args)))
                               (:inline m))
                  t (:tag m)]
              (cond

               macro?
               (let [res (apply v form (:locals env) (rest form))] ; (m &form &env & args)
                 (when-not (ns-safe-macro v)
                    (update-ns-map!))
                 (if (obj? res)
                   (vary-meta res merge (meta form))
                   res))

               inline?
               (let [res (apply inline? args)]
                 (update-ns-map!)
                 (if (obj? res)
                   (vary-meta res merge
                              (and t {:tag t})
                              (meta form))
                   res))

               :else
               (desugar-host-expr form env)))))

        (symbol? form)
        (desugar-symbol form env)

        :else
        form))))

(defn qualify-arglists [arglists]
  (vary-meta arglists merge
             (when-let [t (:tag (meta arglists))]
               {:tag (if (or (string? t)
                             (u/specials (str t))
                             (u/special-arrays (str t)))
                       t
                       (if-let [c (maybe-class t)]
                         (let [new-t (-> c .getName symbol)]
                           (if (= new-t t)
                             t
                             (with-meta new-t {::qualified? true})))
                         t))})))

(defn create-var
  "Creates a Var for sym and returns it.
   The Var gets interned in the env namespace."
  [sym {:keys [ns]}]
  (let [v (get-in (env/deref-env) [:namespaces ns :mappings (symbol (name sym))])]
    (if (and v (or (class? v)
                   (= ns (ns-name (.ns ^Var v) ))))
      v
      (let [meta (dissoc (meta sym) :inline :inline-arities :macro)
            meta (if-let [arglists (:arglists meta)]
                   (assoc meta :arglists (qualify-arglists arglists))
                   meta)]
       (intern ns (with-meta sym meta))))))

(defn parse-monitor-enter
  [[_ target :as form] env]
  (when-not (= 2 (count form))
    (throw (ex-info (str "Wrong number of args to monitor-enter, had: " (dec (count form)))
                    (merge {:form form}
                           (-source-info form env)))))
  {:op       :monitor-enter
   :env      env
   :form     form
   :target   (-analyze target (ctx env :ctx/expr))
   :children [:target]})

(defn parse-monitor-exit
  [[_ target :as form] env]
  (when-not (= 2 (count form))
    (throw (ex-info (str "Wrong number of args to monitor-exit, had: " (dec (count form)))
                    (merge {:form form}
                           (-source-info form env)))))
  {:op       :monitor-exit
   :env      env
   :form     form
   :target   (-analyze target (ctx env :ctx/expr))
   :children [:target]})

(defn parse-import*
  [[_ class :as form] env]
  (when-not (= 2 (count form))
    (throw (ex-info (str "Wrong number of args to import*, had: " (dec (count form)))
                    (merge {:form form}
                           (-source-info form env)))))
  {:op    :import
   :env   env
   :form  form
   :class class})

(defn analyze-method-impls
  [[method [this & params :as args] & body :as form] env]
  (when-let [error-msg (cond
                        (not (symbol? method))
                        (str "Method method must be a symbol, had: " (class method))
                        (not (vector? args))
                        (str "Parameter listing should be a vector, had: " (class args))
                        (not (first args))
                        (str "Must supply at least one argument for 'this' in: " method))]
    (throw (ex-info error-msg
                    (merge {:form     form
                            :in       (:this env)
                            :method   method
                            :args     args}
                           (-source-info form env)))))
  (let [meth        (cons (vec params) body) ;; this is an implicit arg
        this-expr   {:name  this
                     :env   env
                     :form  this
                     :op    :binding
                     :o-tag (:this env)
                     :tag   (:this env)
                     :local :this}
        env         (assoc-in (dissoc env :this) [:locals this] (dissoc-env this-expr))
        method-expr (analyze-fn-method meth env)]
    (assoc (dissoc method-expr :variadic?)
      :op       :method
      :form     form
      :this     this-expr
      :name     (symbol (name method))
      :children (into [:this] (:children method-expr)))))

;; HACK
(defn -deftype [name class-name args interfaces]

  (doseq [arg [class-name name]]
    (memo-clear! members* [arg])
    (memo-clear! members* [(str arg)]))

  (let [interfaces (mapv #(symbol (.getName ^Class %)) interfaces)]
    (eval (list `let []
                (list 'deftype* name class-name args :implements interfaces)
                (list `import class-name)))))

(defn parse-reify*
  [[_ interfaces & methods :as form] env]
  (let [interfaces (conj (disj (set (mapv maybe-class interfaces)) Object)
                         IObj)
        name (gensym "reify__")
        class-name (symbol (str (namespace-munge *ns*) "$" name))
        menv (assoc env :this class-name)
        methods (mapv #(assoc (analyze-method-impls % menv) :interfaces interfaces)
                      methods)]

    (-deftype name class-name [] interfaces)

    (wrapping-meta
     {:op         :reify
      :env        env
      :form       form
      :class-name class-name
      :methods    methods
      :interfaces interfaces
      :children   [:methods]})))

(defn parse-opts+methods [methods]
  (loop [opts {} methods methods]
    (if (keyword? (first methods))
      (recur (assoc opts (first methods) (second methods)) (nnext methods))
      [opts methods])))

(defn parse-deftype*
  [[_ name class-name fields _ interfaces & methods :as form] env]
  (let [interfaces (disj (set (mapv maybe-class interfaces)) Object)
        fields-expr (mapv (fn [name]
                            {:env     env
                             :form    name
                             :name    name
                             :mutable (let [m (meta name)]
                                        (or (and (:unsynchronized-mutable m)
                                                 :unsynchronized-mutable)
                                            (and (:volatile-mutable m)
                                                 :volatile-mutable)))
                             :local   :field
                             :op      :binding})
                          fields)
        menv (assoc env
               :context :ctx/expr
               :locals  (zipmap fields (map dissoc-env fields-expr))
               :this    class-name)
        [opts methods] (parse-opts+methods methods)
        methods (mapv #(assoc (analyze-method-impls % menv) :interfaces interfaces)
                      methods)]

    (-deftype name class-name fields interfaces)

    {:op         :deftype
     :env        env
     :form       form
     :name       name
     :class-name class-name ;; internal, don't use as a Class
     :fields     fields-expr
     :methods    methods
     :interfaces interfaces
     :children   [:fields :methods]}))

(defn parse-case*
  [[_ expr shift mask default case-map switch-type test-type & [skip-check?] :as form] env]
  (let [[low high] ((juxt first last) (keys case-map)) ;;case-map is a sorted-map
        e (ctx env :ctx/expr)
        test-expr (-analyze expr e)
        [tests thens] (reduce (fn [[te th] [min-hash [test then]]]
                                (let [test-expr (ana/analyze-const test e)
                                      then-expr (-analyze then env)]
                                  [(conj te {:op       :case-test
                                             :form     test
                                             :env      e
                                             :hash     min-hash
                                             :test     test-expr
                                             :children [:test]})
                                   (conj th {:op       :case-then
                                             :form     then
                                             :env      env
                                             :hash     min-hash
                                             :then     then-expr
                                             :children [:then]})]))
                              [[] []] case-map)
        default-expr (-analyze default env)]
    {:op          :case
     :form        form
     :env         env
     :test        (assoc test-expr :case-test true)
     :default     default-expr
     :tests       tests
     :thens       thens
     :shift       shift
     :mask        mask
     :low         low
     :high        high
     :switch-type switch-type
     :test-type   test-type
     :skip-check? skip-check?
     :children    [:test :tests :thens :default]}))

(defn parse
  "Extension to tools.analyzer/-parse for JVM special forms"
  [form env]
  ((case (first form)
     monitor-enter        parse-monitor-enter
     monitor-exit         parse-monitor-exit
     clojure.core/import* parse-import*
     reify*               parse-reify*
     deftype*             parse-deftype*
     case*                parse-case*
     #_:else              ana/-parse)
   form env))

(def default-passes
  "Set of passes that will be run by default on the AST by #'run-passes"
  #{#'warn-on-reflection
    #'warn-earmuff

    #'uniquify-locals

    #'source-info
    #'elide-meta
    #'constant-lift

    #'trim

    #'box

    #'analyze-host-expr
    #'validate-loop-locals
    #'validate
    #'infer-tag

    #'classify-invoke})

(def scheduled-default-passes
  (schedule default-passes))

(defn ^:dynamic run-passes
  "Function that will be invoked on the AST tree immediately after it has been constructed,
   by default runs the passes declared in #'default-passes, should be rebound if a different
   set of passes is required.

   Use #'clojure.tools.analyzer.passes/schedule to get a function from a set of passes that
   run-passes can be bound to."
  [ast]
  (scheduled-default-passes ast))

(def default-passes-opts
  "Default :passes-opts for `analyze`"
  {:collect/what                    #{:constants :callsites}
   :collect/where                   #{:deftype :reify :fn}
   :collect/top-level?              false
   :collect-closed-overs/where      #{:deftype :reify :fn :loop :try}
   :collect-closed-overs/top-level? false})

(defn analyze
  "Analyzes a clojure form using tools.analyzer augmented with the JVM specific special ops
   and returns its AST, after running #'run-passes on it.

   If no configuration option is provides, analyze will setup tools.analyzer using the extension
   points declared in this namespace.

   If provided, opts should be a map of options to analyze, currently the only valid
   options are :bindings and :passes-opts (if not provided, :passes-opts defaults to the
   value of `default-passes-opts`).
   If provided, :bindings should be a map of Var->value pairs that will be merged into the
   default bindings for tools.analyzer, useful to provide custom extension points.
   If provided, :passes-opts should be a map of pass-name-kw->pass-config-map pairs that
   can be used to configure the behaviour of each pass.

   E.g.
   (analyze form env {:bindings  {#'ana/macroexpand-1 my-mexpand-1}})"
  ([form] (analyze form (empty-env) {}))
  ([form env] (analyze form env {}))
  ([form env opts]
     (with-bindings (merge {Compiler/LOADER     (RT/makeClassLoader)
                            #'ana/macroexpand-1 macroexpand-1
                            #'ana/create-var    create-var
                            #'ana/parse         parse
                            #'ana/var?          var?
                            #'elides            (merge {:fn    #{:line :column :end-line :end-column :file :source}
                                                        :reify #{:line :column :end-line :end-column :file :source}}
                                                       elides)
                            #'*ns*              (the-ns (:ns env))}
                           (:bindings opts))
       (env/ensure (global-env)
         (doto (env/with-env (mmerge (env/deref-env)
                                     {:passes-opts (get opts :passes-opts default-passes-opts)})
                 (run-passes (-analyze form env)))
           (do (update-ns-map!)))))))

(deftype ExceptionThrown [e ast])

(defn ^:private throw! [e]
  (throw (.e ^ExceptionThrown e)))

(defn analyze+eval
  "Like analyze but evals the form after the analysis and attaches the
   returned value in the :result field of the AST node.

   If evaluating the form will cause an exception to be thrown, the exception
   will be caught and wrapped in an ExceptionThrown object, containing the
   exception in the `e` field and the AST in the `ast` field.

   The ExceptionThrown object is then passed to `handle-evaluation-exception`,
   which by defaults throws the original exception, but can be used to provide
   a replacement return value for the evaluation of the AST.

   Unrolls `do` forms to handle the Gilardi scenario.

   Useful when analyzing whole files/namespaces."
  ([form] (analyze+eval form (empty-env) {}))
  ([form env] (analyze+eval form env {}))
  ([form env {:keys [handle-evaluation-exception]
              :or {handle-evaluation-exception throw!}
              :as opts}]
     (env/ensure (global-env)
       (update-ns-map!)
       (let [env (merge env (-source-info form env))
             [mform raw-forms] (with-bindings {Compiler/LOADER     (RT/makeClassLoader)
                                               #'*ns*              (the-ns (:ns env))
                                               #'ana/macroexpand-1 (get-in opts [:bindings #'ana/macroexpand-1] macroexpand-1)}
                                 (loop [form form raw-forms []]
                                   (let [mform (ana/macroexpand-1 form env)]
                                     (if (= mform form)
                                       [mform (seq raw-forms)]
                                       (recur mform (conj raw-forms
                                                          (if-let [[op & r] (and (seq? form) form)]
                                                            (if (or (u/macro? op  env)
                                                                    (u/inline? op r env))
                                                              (vary-meta form assoc ::ana/resolved-op (resolve-sym op env))
                                                              form)
                                                            form)))))))]
         (if (and (seq? mform) (= 'do (first mform)) (next mform))
           ;; handle the Gilardi scenario
           (let [[statements ret] (butlast+last (rest mform))
                 statements-expr (mapv (fn [s] (analyze+eval s (-> env
                                                                (ctx :ctx/statement)
                                                                (assoc :ns (ns-name *ns*)))
                                                            opts))
                                       statements)
                 ret-expr (analyze+eval ret (assoc env :ns (ns-name *ns*)) opts)]
             {:op         :do
              :top-level  true
              :form       mform
              :statements statements-expr
              :ret        ret-expr
              :children   [:statements :ret]
              :env        env
              :result     (:result ret-expr)
              :raw-forms  raw-forms})
           (let [a (analyze mform env opts)
                 frm (emit-form a)
                 result (try (eval frm) ;; eval the emitted form rather than directly the form to avoid double macroexpansion
                             (catch Exception e
                               (handle-evaluation-exception (ExceptionThrown. e a))))]
             (merge a {:result    result
                       :raw-forms raw-forms})))))))

(defn analyze-ns
  "Analyzes a whole namespace, returns a vector of the ASTs for all the
   top-level ASTs of that namespace.
   Evaluates all the forms."
  ([ns] (analyze-ns ns (empty-env)))
  ([ns env] (analyze-ns ns env {}))
  ([ns env opts]
     (env/ensure (global-env)
       (let [res ^URL (ns-url ns)]
         (assert res (str "Can't find " ns " in classpath"))
         (let [filename (str res)
               path     (.getPath res)]
           (when-not (get-in (env/deref-env) [::analyzed-clj path])
             (binding [*ns*   *ns*
                       *file* filename]
               (with-open [rdr (io/reader res)]
                 (let [pbr (readers/indexing-push-back-reader
                            (java.io.PushbackReader. rdr) 1 filename)
                       eof (Object.)
                       read-opts {:eof eof :features #{:clj :t.a.jvm}}
                       read-opts (if (.endsWith filename "cljc")
                                   (assoc read-opts :read-cond :allow)
                                   read-opts)]
                   (loop []
                     (let [form (reader/read read-opts pbr)]
                       (when-not (identical? form eof)
                         (swap! *env* update-in [::analyzed-clj path]
                                (fnil conj [])
                                (analyze+eval form (assoc env :ns (ns-name *ns*)) opts))
                         (recur))))))))
           (get-in @*env* [::analyzed-clj path]))))))

(defn macroexpand-all
  "Like clojure.walk/macroexpand-all but correctly handles lexical scope"
  ([form] (macroexpand-all form (empty-env) {}))
  ([form env] (macroexpand-all form env {}))
  ([form env opts]
     (binding [run-passes emit-form]
       (analyze form env opts))))

(comment
  (analyze+eval '(String/.length "foo"))
  (analyze+eval 'String/.length)
  (with-bindings {#'ana/macroexpand-1 macroexpand-1
                  #'ana/parse parse}
    (env/ensure (global-env)
                (-analyze '(fn [x]
                             (String/.length x)) (empty-env))))

  (macroexpand-1 'String/.length)
  (macroexpand-1 'Integer/parseInt)
  (clojure.core/macroexpand-1 'Integer/parseInt)
  (macroexpand-1 'Long/parseLong)
  (eval (macroexpand-1 '(fn [x]
                          (String/.length x))))

  (macroexpand-1 'clojure.lang.Compiler/LOADER)
  (macroexpand-1 '(String/new "foo"))

  )
