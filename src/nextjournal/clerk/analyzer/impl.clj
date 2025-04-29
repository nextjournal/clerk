(ns nextjournal.clerk.analyzer.impl
  (:require [clojure.string :as str])
    (:refer-clojure :exclude [macroexpand-1 macroexpand update-vals])
    (:import (clojure.lang IObj)))

(def ^:dynamic *deps* nil)

;; based off of https://github.com/hyperfiddle/rcf/blob/master/src/hyperfiddle/rcf/analyzer.clj

(defn cljs? [env] (some? (:js-globals env)))

(defn empty-env
  "Returns an empty env"
  []
  {:locals     {}
   :namespaces {}
   :ns         (ns-name *ns*)})

(defn to-env [&env]
  (if (:js-globals &env)
    &env
    (assoc (empty-env) :locals (or &env {}))))

(defn build-ns-map []
  {:namespaces (into {} (mapv #(vector (ns-name %)
                                       {:mappings (merge (ns-map %) {'in-ns #'clojure.core/in-ns})
                                        :aliases  (reduce-kv (fn [a k v] (assoc a k (ns-name v)))
                                                             {} (ns-aliases %))
                                        :ns       (ns-name %)})
                              (all-ns)))})

(def ^:dynamic *global-env* nil)

(defn global-env [] (or *global-env* (build-ns-map)))

(defn resolve-local [env sym] (get-in env [:locals sym]))

(defn resolve-ns
  "Resolves the ns mapped by the given sym in the global env"
  [ns-sym {:keys [ns]}]
  (when ns-sym
    (let [namespaces (:namespaces (global-env))]
      (or (get-in namespaces [ns :aliases ns-sym])
          (:ns (namespaces ns-sym))))))

(defn var?' [maybe-var] (or (var? maybe-var) (= ::var (type maybe-var))))

(defn to-var [{:keys [macro meta ns name]}]
  (with-meta {:ns ns, :name name} (assoc meta :type ::var)))

(defmacro no-warn
  "Localy disable a set of cljs compiler warning.
  Usage: `(no-warn #{:undeclared-ns} (cljs/resolve env sym))`"
  [disabled-warnings & body]
  ;; Cannot use `cc/binding` as it relies on var which does a read-time resolve,
  ;; while we want a runtime var resolve.
  `(do (push-thread-bindings {(resolve 'cljs.analyzer/*cljs-warnings*)
                              (reduce (fn [r# k#] (assoc r# k# false))
                                      (deref (resolve 'cljs.analyzer/*cljs-warnings*))
                                      ~disabled-warnings)})
       (try ~@body
            (finally (pop-thread-bindings)))))

(defn cljs-resolve [env sym]
  (require '[cljs.analyzer.api])
  (require '[cljs.analyzer])
  ;; RCF should try to resolve like the repl does, but is not in charge of
  ;; handling invalid userland forms.
  (no-warn #{:undeclared-ns} ((resolve 'cljs.analyzer.api/resolve) env sym)))

(defn resolve-sym
  "Resolves the value mapped by the given sym in the global env"
  [sym {:keys [ns] :as env}]
  (when (symbol? sym)
    (if (cljs? env)
      (let [resolved (cljs-resolve env sym)]
        (if (or (:macro resolved) (= :var (:op resolved)))
          (resolve-sym (:name resolved) (dissoc env :js-globals))
          (to-var resolved)))
      (let [sym-ns  (when-let [ns (namespace sym)] (symbol ns))
            full-ns (resolve-ns sym-ns env)]
        (when (or (not sym-ns) full-ns)
          (let [name (if sym-ns (-> sym name symbol) sym)]
            (-> (global-env) :namespaces (get (or full-ns ns)) :mappings (get name))))))))

(def specials "Set of special forms common to every clojure variant" ;; TODO replace with cc/special-symbol?
  '#{do if new quote set! try var catch throw finally def . let* letfn* loop* recur fn*})

(defn var-sym [v]
  (cond
    (var? v) (symbol v)
    (var?' v) (symbol (str (:ns v)) (str (:name v)))))

#_(ns-unmap *ns* 'macroexpand-hook)

(defmulti macroexpand-hook (fn [the-var _&form _&env _args] (var-sym the-var)))

(defmethod macroexpand-hook 'clojure.core/deftype [_ &form &env [name fields & opts+specs]]
  (when-not (resolve name)
    (apply #'clojure.core/deftype &form &env name fields opts+specs)))

(defmethod macroexpand-hook :default [the-var &form &env args]
  (if (cljs? &env)
    (if (:cljs.analyzer/numeric (meta the-var))
      (reduced &form)
      (let [mform (apply the-var &form &env args)]
        (if (and (seq? mform) (= 'js* (first mform)))
          (reduced &form)
          mform)))
    (apply the-var &form (:locals &env) args)))

(defn has-meta? [o] (instance? clojure.lang.IMeta o))

(defmulti -parse (fn [_env form] (and (seq? form) (first form))))

(defn parse
  ([]    (parse (empty-env)))
  ([env] (partial parse env))
  ([env form]
   {:pre (map? env)}
   (-parse env form)))

(defn classify
  "Returns a keyword describing the form type"
  [form]
  (cond
    ;; (nil? form)     :nil
    ;; (boolean? form) :bool
    ;; (keyword? form) :keyword
    (symbol? form)  :symbol
    ;; (string? form)  :string
    ;; (number? form)  :number
    ;; (type? form)    :type
    ;; (record? form)  :record
    (map? form)     :map
    (vector? form)  :vector
    (set? form)     :set
    (seq? form)     :seq
    ;; (char? form)    :char
    ;; (regex? form)   :regex
    ;; (class? form)   :class
    (var? form)     :var
    :else           :const))

(defmulti -analyze (fn [env form]
                     {:pre (map? env)}
                     (classify form)))

(defn analyze*
  ([env]      (partial analyze* env))
  ([env form] (-analyze env form)))

(defn obj? [x] (instance? IObj x))

(defn- analyze-const [env form]
  {:op   :const
   :env  env
   :form form})

(defmethod -analyze :const [env form] (analyze-const env form))

(defn analyze-body [env body]
  ;; :body is used by emit-form to remove the artificial 'do
  (assoc (parse env (cons 'do body)) :body? true)) ;; default

(defn wrapping-meta
  [{:keys [form env] :as expr}]
  (let [meta (meta form)]
    (if (and (obj? form)
             (seq meta))
      {:op       :with-meta
       :env      env
       :form     form
       :meta     meta
       :expr     expr
       :children [:meta :expr]}
      expr)))

(defmethod -analyze :vector [env form]
  (let [items (mapv (analyze* env) form)]
    (wrapping-meta
     {:op       :vector
      :env      env
      :items    items
      :form     form
      :children [:items]})))

(defmethod -analyze :map [env form]
  (wrapping-meta
   {:op       :map
    :env      env
    :keys     (mapv (analyze* env) (keys form))
    :vals     (mapv (analyze* env) (vals form))
    :form     form
    :children [:keys :vals]}))

(defmethod -analyze :set [env form]
  (wrapping-meta
   {:op       :set
    :env      env
    :items    (mapv (analyze* env) form)
    :form     form
    :children [:items]}))

(defmethod -analyze :seq [env form]
  (if (not (seq form))
    (analyze-const env form)
    (parse env form)))

(defmethod -analyze :symbol [env sym]
  (let [local? (some? (resolve-local env sym))]
    {:op :symbol
     :local? local?
     :env  env
     :form sym
     :ns   (namespace sym)
     :name (name sym)}))

(defmethod -analyze :var [env form]
  {:op :the-var
   :env env
   :var form})

;; --------

(defn valid-binding-symbol? [s]
  (and (symbol? s)
       (not (namespace s))
       (not (re-find #"\." (name s)))))

(defn source-info
  "Returns the available source-info keys from a map"
  [m]
  (when (:line m)
    (select-keys m #{:file :line :column :end-line :end-column :source-span})))

(defn -source-info
  "Returns the source-info of x"
  [env x]
  (merge (source-info env)
         (source-info (meta x))
         (when-let [file (and (not= *file* "NO_SOURCE_FILE")
                              *file*)]
           {:file file})))

(defn validate-bindings
  [[op bindings & _ :as form] env]
  (when-let [error-msg
             (cond
               (not (vector? bindings))
               (str op " requires a vector for its bindings, had: "
                    (class bindings))

               (not (even? (count bindings)))
               (str op " requires an even number of forms in binding vector, had: "
                    (count bindings)))]
    (throw (ex-info error-msg
                    (merge {:form     form
                            :bindings bindings}
                           (-source-info env form))))))

(defn dissoc-env [ast] (dissoc ast :env))

(defn analyze-let [env [_ bindings & body :as form]]
  (validate-bindings env form)
  (loop [bindings bindings
         env      env
         binds    []]
    (if-let [[name init & bindings] (seq bindings)]
      (if (not (valid-binding-symbol? name))
        (throw (ex-info (str "Bad binding form: " name)
                        (merge {:form form
                                :sym  name}
                               (-source-info form env))))
        (let [bind-expr {:op       :binding
                         :env      env
                         :name     name
                         :init     (analyze* env init)
                         :form     name
                         :local    :let
                         :children [:init]}]
          (recur bindings
                 (assoc-in env [:locals name] (dissoc-env bind-expr))
                 (conj binds bind-expr))))
      {:body     (analyze-body env body)
       :bindings binds
       :children [:bindings :body]})))

(defn analyze-fn-method [{:keys [locals local] :as env} [params & body :as form]]
  (when-not (vector? params)
    (throw (ex-info "Parameter declaration should be a vector"
                    (merge {:params params
                            :form   form}
                           (-source-info form env)
                           (-source-info params env)))))
  (when (not-every? valid-binding-symbol? params)
    (throw (ex-info (str "Params must be valid binding symbols, had: "
                         (mapv class params))
                    (merge {:params params
                            :form   form}
                           (-source-info form env)
                           (-source-info params env))))) ;; more specific
  (let [variadic?    (boolean (some '#{&} params))
        params-names (if variadic? (conj (pop (pop params)) (peek params)) params)
        env          (dissoc env :local)
        arity        (count params-names)
        params-expr  (mapv (fn [name id]
                             {:op        :binding
                              :env       env
                              :form      name
                              :name      name
                              :variadic? (and variadic?
                                              (= id (dec arity)))
                              :arg-id    id
                              :local     :arg})
                           params-names (range))
        fixed-arity  (if variadic?
                       (dec arity)
                       arity)
        body-env (update-in env [:locals] merge (zipmap params-names (map dissoc-env params-expr)))
        body         (analyze-body body-env body)]
    (when variadic?
      (let [x (drop-while #(not= % '&) params)]
        (when (contains? #{nil '&} (second x))
          (throw (ex-info "Invalid parameter list"
                          (merge {:params params
                                  :form   form}
                                 (-source-info form env)
                                 (-source-info params env)))))
        (when (not= 2 (count x))
          (throw (ex-info (str "Unexpected parameter: " (first (drop 2 x))
                               " after variadic parameter: " (second x))
                          (merge {:params params
                                  :form   form}
                                 (-source-info form env)
                                 (-source-info params env)))))))
    (merge
     {:op          :fn-method
      :form        form
      :env         env
      :variadic?   variadic?
      :params      params-expr
      :fixed-arity fixed-arity
      :body        body
      :children    [:params :body]}
     (when local
       {:local (dissoc-env local)}))))

(defmethod -parse 'do [env [_ & exprs :as form]]
  {:op         :do
   :env        env
   :form       form
   :statements (mapv (analyze* env) exprs)
   :children   [:statements]})

(defmethod -parse 'if [env [_ test then else :as form]]
  {:op       :if
   :form     form
   :env      env
   :test     (analyze* env test)
   :then     (analyze* env then)
   :else     (analyze* env else)
   :children [:test :then :else]})

(defmethod -parse 'quote [env form]
  {:op       :quote
   ;; :expr     (analyze env expr) ;; maybe not needed
   :form     form
   :env      env
   :children []})

(defmethod -parse 'try [env [_ & body :as form]]
  (let [catches (filter (every-pred seq? #(= 'catch (first %))) body)
        finallies (filter (every-pred seq? #(= 'finally (first %))) body)
        body    (remove (into #{} (concat catches finallies)) body)]
    {:op       :try
     :env      env
     :form     form
     :body     (analyze-body env body)
     :catches  (mapv (partial -parse env) catches)
     :finally  (mapv (partial -parse env) finallies)
     :children [:body :catches :finally]}))

(defmethod -parse 'catch [env [_ etype ename & body :as form]]
  (let [local {:op   :binding
               :env  env
               :form ename
               :name ename
               :local :catch ;; maybe not needed
               }]
    {:op       :catch
     :class    (analyze* (assoc env :locals {}) etype)
     :local    local
     :env      env
     :form     form
     :body     (analyze-body (assoc-in env [:locals ename] (dissoc-env local)) body)
     :children [:local :body]}))

(defmethod -parse 'let* [env form]
  (into {:op   :let
         :form form
         :env  env}
        (analyze-let env form)))

(defmethod -parse 'loop* [env form]
  (into {:op      :loop
         :form    form
         :env     env}
        (analyze-let env form)))

(defmethod -parse :default [env form]
  (if (seq? form)
    (let [[f & args] form]
      {:op       :invoke
       :form     form
       :env      env
       :fn       (analyze* env f)
       :args     (mapv (analyze* env) args)
       :children [:fn :args]})
    (analyze* env form)))

(defn ns-sym [ns] (cond (symbol? ns) ns
                        (map? ns) (:name ns)
                        :else (ns-name ns)))

(defn unquote' [form]
  (if (and (seq? form) (= 'quote (first form)))
    (second form)
    form))

(defn update-vals
  "Applies f to all the vals in the map"
  [m f]
  (reduce-kv (fn [m k v] (assoc m k (f v))) {} (or m {})))

(defmacro if-bb [then else] (if (System/getProperty "babashka.version") then else))

(defn create-var
  "Creates a Var for sym and returns it.
   The Var gets interned in the env namespace."
  [sym {:keys [ns] :as env}]
  (let [v (get-in (global-env) [:namespaces ns :mappings (symbol (name sym))])]
    (if (some? v)
      (cond
        (class? v) v
        (and (var? v) (= ns (ns-name (if-bb (:ns (meta v)) (.ns ^clojure.lang.Var v)))))
        (do (when-some [m (meta sym)]
              (if-bb (alter-meta! v (constantly (update-vals m unquote')))
                     (.setMeta v (update-vals m unquote')))) v)
        :else (throw (ex-info (str "(def " sym " ...) resolved to an existing mapping of an unexpected type.")
                              {:sym         sym
                               :ns          ns
                               :resolved-to v
                               :type        (type v)})))
      (let [meta (-> (dissoc (meta sym) :inline :inline-arities #_:macro)
                     (update-vals unquote'))
            #_#_meta (if-let [arglists (:arglists meta)]
                       (assoc meta :arglists (qualify-arglists arglists))
                       meta)]
        (intern (ns-sym ns) (with-meta sym meta))))))

(defn- to-cljs-var [var]
  (let [m (-> (meta var))
        m (as-> m $
            (update $ :ns ns-name)
            (assoc $ :name (symbol (str (:ns $)) (str (:name $)))))]
    (assoc m :meta m)))

(defn- intern-cljs-var! [cljs-var]
  (require 'cljs.env)
  (let [ns (:ns cljs-var)
        name (symbol (name (:name cljs-var)))
        *compiler* (deref (resolve 'cljs.env/*compiler*))]
    (swap! *compiler* assoc-in [:cljs.analyzer/namespaces ns :defs name] cljs-var)
    nil))

(defmethod -parse 'def [{:keys [ns] :as env} [_ sym & expr :as form]]
  (def s* sym)
  (let [pfn  (fn
               ([])
               ([init]
                {:init init})
               ([doc init]
                {:pre [(string? doc)]}
                {:init init :doc doc}))
        args (apply pfn expr)
        env (if (some? (namespace sym))
              env ;; Can't intern namespace-qualified symbol, ignore
              (let [var (create-var sym env)] ;; side effect, FIXME should be a pass
                (when (cljs? env)
                  (intern-cljs-var! (to-cljs-var var)))
                (assoc-in env [:namespaces ns :mappings sym] var)))
        args (when-let [[_ init] (find args :init)]
               (assoc args :init (analyze* env init)))]
    (merge {:op       :def
            :env      env
            :form     form
            :name     sym
            :doc      (or (:doc args) (-> sym meta :doc))
            :children (into [:meta] (when (:init args) [:init]))
            :var (get-in env [:namespaces ns :mappings sym])
            :meta {:val (meta sym)}}
           args)))

(defmethod -parse 'fn* [env [op & args :as form]]
  (wrapping-meta
   (let [[n meths]       (if (symbol? (first args))
                           [(first args) (next args)]
                           [nil (seq args)])
         name-expr       {:op    :binding
                          :env   env
                          :form  n
                          :local :fn
                          :name  n}
         e               (if n (assoc (assoc-in env [:locals n] (dissoc-env name-expr)) :local name-expr) env)
         once?           (-> op meta :once boolean)
         menv            (assoc e :once once?)
         meths           (if (vector? (first meths)) (list meths) meths) ;;turn (fn [] ...) into (fn ([]...))
         methods-exprs   (mapv #(analyze-fn-method menv %) meths)]
     (merge {:op              :fn
             :env             env
             :form            form
             :methods         methods-exprs
             :once            once?}
            (when n
              {:local name-expr})
            {:children (conj (if n [:local] []) :methods)}))))

(defmethod -parse 'letfn* [env [_ bindings & body :as form]]
  (validate-bindings env form)
  (let [bindings (apply array-map bindings)            ;; pick only one local with the same name, if more are present.
        fns      (keys bindings)
        binds    (reduce (fn [binds name]
                           (assoc binds name
                                  {:op    :binding
                                   :env   env
                                   :name  name
                                   :form  name
                                   :local :letfn}))
                         {} fns)
        e        (update-in env [:locals] merge binds) ;; pre-seed locals
        binds    (reduce-kv (fn [binds name bind]
                              (assoc binds name
                                     (merge bind
                                            {:init     (analyze* e (bindings name))
                                             :children [:init]})))
                            {} binds)
        e        (update-in env [:locals] merge (update-vals binds dissoc-env))]
    {:op       :letfn
     :env      env
     :form     form
     :bindings (vec (vals binds)) ;; order is irrelevant
     :body     (analyze-body e body)
     :children [:bindings :body]}))

;;;;;;;;;;
;; EMIT ;;
;;;;;;;;;;

(def ^:dynamic *emit-options* {:simplify-do false}) ;; FIXME :simplify-* should be passes

(defmulti -emit (fn [ast] (:op ast)))

(defn emit [ast] (-emit ast))

(defmethod -emit :const [ast] (:form ast))

(defmethod -emit :symbol [ast] (:form ast))
(defmethod -emit :var [ast] (:form ast))

(defn emit-invoke [ast] (list* (emit (:fn ast)) (mapv emit (:args ast))))
(defmethod -emit :invoke [ast] (emit-invoke ast))

(defmethod -emit :do [ast]
  (if (and (:simplify-do *emit-options*)
           (= 1 (count (:statements ast))))
    (emit (first (:statements ast)))
    (list* 'do (mapv emit (:statements ast)))))

(defmethod -emit :vector [ast] (mapv emit (:items ast)))
(defmethod -emit :set [ast] (set (mapv emit (:items ast))))
(defmethod -emit :map [ast] (zipmap (mapv emit (:keys ast))
                                    (mapv emit (:vals ast))))

(defmethod -emit :with-meta [ast] (with-meta (emit (:expr ast)) (:meta ast)))

(defmethod -emit :try [ast] (list* 'try (emit (:body ast))
                                   (concat (mapv emit (:catches ast))
                                           (mapv emit (:finally ast)))))
(defmethod -emit :catch [ast] (list 'catch (emit (:class ast)) (emit (:local ast)) (emit (:body ast))))

(defmethod -emit :binding [ast]
  (case (:local ast)
    :catch        (:form ast)
    (:let :letfn) [(:name ast) (emit (:init ast))]
    :fn           (:name ast)
    :arg          (if (:variadic? ast)
                    ['& (:name ast)]
                    [(:name ast)])))

(defmethod -emit :quote [ast] (:form ast))

(defmethod -emit :if [ast] (list 'if (emit (:test ast)) (emit (:then ast)) (emit (:else ast))))

(defmethod -emit :def [ast]
  (if-let [init (:init ast)]
    (list 'def (:name ast) (emit init))
    (list 'def (:name ast))))

(defmethod -emit :let [ast]
  (list* 'let* (vec (mapcat identity (mapv emit (:bindings ast))))
         (if (:simplify-do *emit-options*) ;; FIXME should be a pass
           (mapv emit (:statements (:body ast)))
           (list (emit (:body ast))))))

(defmethod -emit :loop [ast]
  (list 'loop* (vec (mapcat identity (mapv emit (:bindings ast)))) (emit (:body ast))))

(defmethod -emit :fn [ast]
  (let [methods (mapv emit (:methods ast))]
    (if-let [name (some-> (:local ast) emit)]
      `(~'fn* ~name ~@methods)
      `(~'fn* ~@methods))))

(defmethod -emit :fn-method [ast]
  (list (vec (mapcat emit (:params ast))) (emit (:body ast))))

(defmethod -emit :letfn [ast]
  (list 'letfn* (vec (mapcat identity (mapv emit (:bindings ast)))) (emit (:body ast))))

;; AST walk

(defn walk [inner outer ast]
  (when (some? ast)
    (if (reduced? ast) ast
        (outer (reduce (fn [ast child-key]
                         (if (sequential? (get ast child-key))
                           (update ast child-key (partial mapv inner))
                           (update ast child-key inner)))
                       ast (:children ast))))))

(defn postwalk [f ast] (unreduced (walk (partial postwalk f) f ast)))
(defn prewalk [f ast] (unreduced (walk (partial prewalk f) identity (f ast))))

(defn only-nodes [pred f] ;; use with *walk to skip some nodes
  (let [pred (if (set? pred) (comp pred :op) pred)]
    (fn [ast] (if (pred ast) (f ast) ast))))

(defn children [ast]
  (when (some? ast)
    (mapcat (fn [child]
              (let [child-ast (get ast child)]
                (if (sequential? child-ast)
                  child-ast
                  (list child-ast))))
            (:children ast))))

(defn ast-seq
  "Equivalent of `cc/tree-seq` on AST nodes"
  [ast]
  (when (some? ast)
    (cons ast (mapcat ast-seq (children ast)))))

;; Passes

(defn resolve-sym-node [{:keys [env] :as ast}]
  (assert (= :symbol (:op ast)))
  (if (:local? ast)
    ast
    (if-let [v (resolve-sym (:form ast) env)]
      (if (var?' v)
        (assoc ast :op :var, :var v)
        ast)
      ast)))

(defn resolve-syms-pass [ast] (prewalk (only-nodes #{:symbol} resolve-sym-node) ast))

(defn- tag-with-form [ast parent form] (assoc ast :raw-forms (conj (:raw-forms parent ()) (list 'quote form))))

(defn macroexpand-node [{:keys [env] :as ast}]
  (let [{:keys [op var]} (:fn ast)
        [f & args :as form] (:form ast)]
    (cond
      (and (symbol? f) (not= '. f) (str/starts-with? (name f) "."))
      (analyze* env (list* '. (symbol (subs (name f) 1)) args))
      (and (= :var op) (:macro (meta var)) (not (::prevent-macroexpand (meta f))))
      (do
        (swap! *deps* conj var) ;; collect macro var
        (let [mform (macroexpand-hook var form env args)
              var'  (when (seq? mform) (resolve-sym (first mform) env))]
            (cond
              (= form mform)   (reduced ast)
              (reduced? mform) (reduced (tag-with-form (parse env (unreduced mform)) ast form))
              (= var var')     (let [[f & args] mform
                                     f (if (contains? (methods macroexpand-hook) f)
                                         (vary-meta f assoc ::prevent-macroexpand true)
                                         f)]
                                 (tag-with-form (analyze* env (cons f args)) ast form))
              :else            (tag-with-form (analyze* env mform) ast form))))
      :else ast)))

(defn macroexpand-pass
  ([ast] (macroexpand-pass ##Inf ast))
  ([n ast]
   (let [state (atom n)]
     (prewalk (only-nodes #{:invoke}
                          (fn rec [ast]
                            (if-not (pos? @state)
                              (reduced ast) ;; stop walking
                              (let [ast' (macroexpand-node ast)]
                                (binding [*global-env* (build-ns-map)]
                                  (let [ast'-resolved (resolve-syms-pass (unreduced ast'))]
                                    (cond
                                      (reduced? ast') (reduced ast'-resolved)
                                      (= ast ast')    ast'-resolved
                                      :else           (if (pos? (swap! state dec))
                                                        (if (= :invoke (:op ast'-resolved))
                                                          (rec ast'-resolved)
                                                          ast'-resolved)
                                                        ast'-resolved))))))))
              ast))))

(defn macroexpand-n
  ([n form] (macroexpand-n n (empty-env) form))
  ([n env form]
   (binding [*global-env* (build-ns-map)]
     (->> (analyze* env form)
          (resolve-syms-pass)
          (macroexpand-pass n)
          (emit)))))

(defn macroexpand-all
  ([form] (macroexpand-all (empty-env) form))
  ([env form] (macroexpand-n ##Inf env form)))

(defn macroexpand-1
  ([form] (macroexpand-1 (empty-env) form))
  ([env form] (macroexpand-n 1 env form)))

(comment
  (macroexpand-all '(String/new "dude"))
  (defn foo [])
  (-parse {} '(foo 1 2 3))
  (defmacro dude [x] x)
  (-parse {} '(dude (foo 1 2 3)))
  (macroexpand-all '(dude (foo 1 2 3)))
  (binding [*ns* *ns*]
    (let [env (to-env {})
          exprs '[(assoc {} :a 1) String]]
      (binding [*emit-options* {:simplify-do true}]
        (->> (cons 'do exprs)
             (analyze* env)
             (resolve-syms-pass)
             (macroexpand-pass)
             #_(rewrite env)
             #_(ana/emit)))))
  )

(defn nodes
  "Returns a lazy-seq of all the nodes in the given AST, in depth-first pre-order."
  [ast]
  (lazy-seq
   (cons ast (mapcat nodes (children ast)))))
