(ns nextjournal.clerk.eval
  "Clerk's incremental evaluation (Babashka Edition) with in-memory caching layer."
  (:refer-clojure :exclude [read-string])
  (:require [clojure.string :as str]
            [edamame.core :as edamame]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.viewer :as v]))

(defn wrapped-with-metadata [value hash]
  (cond-> {:nextjournal/value value}
    ;; TODO: maybe fix hash for blob serving
    hash (assoc :nextjournal/blob-id (cond-> hash (not (string? hash)) str #_ multihash/base58))))

#_(wrap-with-blob-id :test "foo")

(defn elapsed-ms [from]
  (/ (double (- (. System (nanoTime)) from)) 1000000.0))

(defmacro time-ms
  "Pure version of `clojure.core/time`. Returns a map with `:result` and `:time-ms` keys."
  [expr]
  `(let [start# (System/nanoTime)
         ret# ~expr]
     {:result ret#
      :time-ms (elapsed-ms start#)}))

(defn ^:private var-from-def [var]
  (let [resolved-var (cond (var? var)
                           var

                           (symbol? var)
                           (find-var var)

                           :else
                           (throw (ex-info "Unable to resolve into a variable" {:data var})))]
    {:nextjournal.clerk/var-from-def resolved-var}))

(defn ^:private eval-form [{:keys [form var no-cache?]} hash]
  (try
    (let [{:keys [result]} (time-ms (binding [config/*in-clerk* true]
                                      (eval form)))
          result (if (and (nil? result) var (= 'defonce (first form)))
                   (find-var var)
                   result)
          var-value (cond-> result (and var (var? result)) deref)
          no-cache? (or no-cache? config/cache-disabled?)]
      (let [blob-id (cond no-cache? "valuehash" #_#_ TODO?/valuehash (analyzer/->hash-str var-value)
                          (fn? var-value) nil
                          :else hash)
            result (if var (var-from-def var) result)]
        (wrapped-with-metadata result blob-id)))
    (catch Throwable t
      (throw (ex-info (ex-message t) (Throwable->map t))))))

(defn maybe-eval-viewers [{:as opts :nextjournal/keys [viewer viewers]}]
  (cond-> opts
    viewer
    (update :nextjournal/viewer eval)
    viewers
    (update :nextjournal/viewers eval)))

(defn read+eval-cached [{:as _doc :keys [blob->result]} {:as codeblock :keys [form vars var ns-effect? no-cache?]}]
  (let [no-cache? (or ns-effect? no-cache?)
        ;; TODO: hash for in-memory cache
        hash (str (gensym))
        opts-from-form-meta (-> (meta form)
                                (select-keys [:nextjournal.clerk/viewer :nextjournal.clerk/viewers :nextjournal.clerk/width :nextjournal.clerk/opts])
                                v/normalize-viewer-opts
                                maybe-eval-viewers)]
    (cond-> (or (when-let [cached-result (and (not no-cache?) (get-in blob->result [hash :nextjournal/value]))]
                  (wrapped-with-metadata cached-result hash))
                (eval-form codeblock hash))
      (seq opts-from-form-meta)
      (merge opts-from-form-meta))))

(defn eval-analyzed-doc [{:as analyzed-doc :keys [ns blocks]}]
  (let [{:as evaluated-doc :keys [blob-ids]}
        (reduce (fn [state {:as cell :keys [type]}]
                  (let [{:as result :nextjournal/keys [blob-id]} (when (= :code type) (read+eval-cached state cell))]
                    (cond-> (update state :blocks conj (cond-> cell result (assoc :result result)))
                      blob-id (update :blob-ids conj blob-id)
                      blob-id (assoc-in [:blob->result blob-id] result))))
                (assoc analyzed-doc :blocks [] :blob-ids #{})
                blocks)]
    (-> evaluated-doc
        (cond-> (not ns) (assoc :ns (find-ns 'user)))
        (update :blob->result select-keys blob-ids)
        (dissoc :blob-ids))))

(defn read-string [s]
  (edamame/parse-string s
                        {:all true
                         :readers *data-readers*
                         :read-cond :allow
                         :regex #(list `re-pattern %)
                         :features #{:clj}
                         :auto-resolve (as-> (ns-aliases (or *ns* (find-ns 'user))) $
                                         (zipmap (keys $) (map ns-name (vals $)))
                                         (assoc $ :current (ns-name *ns*)))}))

(defn deflike? [form] (and (seq? form) (symbol? (first form)) (str/starts-with? (name (first form)) "def")))
#_(deflike? (read-string "(def ^{:doc \"aloha\"} foo 123)"))
#_(deflike? (read-string "(def ^{:doc \"aloha\"} foo 123)"))

(defn read-forms [doc]
  (binding [*ns* *ns*]
    (reduce (fn [doc {:as b :keys [type text]}]
              (let [form (read-string text)
                    ns? (= 'ns (when (list? form) (first form)))
                    var (when (and (deflike? form) (symbol? (second form))) (second form))]
                (when ns? (eval form))
                (-> doc
                    (cond-> (and ns? (not (:ns doc))) (assoc :ns *ns*))
                    (update :blocks conj
                            (cond-> b
                              (= :code type) (assoc :form form)
                              ns? (assoc :no-cache? true)
                              var (assoc :var (symbol (name (ns-name *ns*)) (name var))))))))
            (assoc doc :blocks [])
            (:blocks doc))))

#_(read-forms
   (parser/parse-file "notebooks/hello.clj"))

;; used in builder
(def analyze-doc read-forms)

(defn +eval-results
  "Evaluates the given `parsed-doc` using the `in-memory-cache` and augments it with the results."
  [in-memory-cache parsed-doc]
  (let [{:as doc :keys [ns]} (read-forms parsed-doc)]
    (binding [*ns* (or ns *ns*)]
      (-> doc
          (assoc :blob->result in-memory-cache)
          eval-analyzed-doc))))

(defn eval-doc
  "Evaluates the given `doc`."
  ([doc] (eval-doc {} doc))
  ([in-memory-cache doc] (+eval-results in-memory-cache doc)))

(defn eval-file
  "Reads given `file` (using `slurp`) and evaluates it."
  ([file] (eval-file {} file))
  ([in-memory-cache file]
   (->> file
        (parser/parse-file {:doc? true})
        (eval-doc in-memory-cache))))

#_(eval-file "notebooks/hello.clj")
#_(eval-file "notebooks/rule_30.clj")
#_(eval-file "notebooks/visibility.clj")

(defn eval-string
  "Evaluated the given `code-string` using the optional `in-memory-cache` map."
  ([code-string] (eval-string {} code-string))
  ([in-memory-cache code-string]
   (eval-doc in-memory-cache (parser/parse-clojure-string {:doc? true} code-string))))

#_(eval-string "(+ 39 3)")
