(ns nextjournal.clerk.eval
  "Clerk's incremental evaluation with in-memory and disk-persisted caching layers."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.main :as main]
            [clojure.string :as str]
            [multiformats.base.b58 :as b58]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.session :as session]
            [nextjournal.clerk.viewer :as v]
            [taoensso.nippy :as nippy])
  (:import (java.awt.image BufferedImage)
           (javax.imageio ImageIO)))

(comment
  (alter-var-root #'nippy/*freeze-serializable-allowlist* (fn [_] "allow-and-record"))
  (alter-var-root   #'nippy/*thaw-serializable-allowlist* (fn [_] "allow-and-record"))
  (nippy/get-recorded-serializable-classes))

;; nippy tweaks
(alter-var-root #'nippy/*thaw-serializable-allowlist* (fn [_] (conj nippy/default-thaw-serializable-allowlist "java.io.File" "clojure.lang.Var" "clojure.lang.Namespace")))
(nippy/extend-freeze BufferedImage :java.awt.image.BufferedImage [x out] (ImageIO/write x "png" (ImageIO/createImageOutputStream out)))
(nippy/extend-thaw :java.awt.image.BufferedImage [in] (ImageIO/read in))

#_(-> [(clojure.java.io/file "notebooks") (find-ns 'user)] nippy/freeze nippy/thaw)


(defn ->cache-file [hash]
  (str config/cache-dir fs/file-separator hash))

(defn wrapped-with-metadata [value hash]
  (cond-> {:nextjournal/value value}
    hash (assoc :nextjournal/blob-id (cond-> hash (not (string? hash)) b58/format-btc))))

#_(wrap-with-blob-id :test "foo")

(defn hash+store-in-cas! [x]
  (let [^bytes ba (nippy/freeze x)
        multihash (analyzer/sha2-base58 ba)
        file (->cache-file multihash)]
    (when-not (fs/exists? file)
      (with-open [out (io/output-stream (io/file file))]
        (.write out ba)))
    multihash))

(defn thaw-from-cas [hash]
  ;; TODO: validate hash and retry or re-compute in case of a mismatch
  (nippy/thaw-from-file (->cache-file hash)))

#_(thaw-from-cas (hash+store-in-cas! (range 42)))
#_(thaw-from-cas "8Vv6q6La171HEs28ZuTdsn9Ukg6YcZwF5WRFZA1tGk2BP5utzRXNKYq9Jf9HsjFa6Y4L1qAAHzMjpZ28TCj1RTyAdx")

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

(defn ^:private lookup-cached-result [introduced-var hash cas-hash]
  (when-let [cached-value (try (thaw-from-cas cas-hash)
                               (catch Exception _e
                                 ;; TODO better report this error, anything that can't be read shouldn't be cached in the first place
                                 #_(prn :thaw-error e)
                                 nil))]
    (wrapped-with-metadata (if introduced-var
                             (var-from-def (intern (-> introduced-var namespace symbol)
                                                   (-> introduced-var name symbol)
                                                   cached-value))
                             cached-value)
                           hash)))


(defn ^:private cachable-value? [value]
  (and (some? value)
       (try
         (and (not (analyzer/exceeds-bounded-count-limit? value))
              (some? (nippy/freezable? value)))
         ;; can error on e.g. lazy-cat fib
         ;; TODO: propagate error information here
         (catch Exception _
           false))))

#_(cachable-value? (vec (range 100)))
#_(cachable-value? (range))
#_(cachable-value? (map inc (range)))
#_(cachable-value? [{:hello (map inc (range))}])


(defn ^:private cache! [digest-file var-value]
  (try
    (spit digest-file (hash+store-in-cas! var-value))
    (catch Exception e
      #_(prn :freeze-error e)
      nil)))

(defn ^:private record-interned-symbol [store ns sym]
  (swap! store conj (symbol (name (ns-name (the-ns ns))) (name sym))))

(def ^:private core-intern intern)

(defn ^:private intern+record
  ([store ns name] (record-interned-symbol store ns name) (core-intern ns name))
  ([store ns name val] (record-interned-symbol store ns name) (core-intern ns name val)))

(defn ^:private eval+cache! [{:keys [form var ns-effect? no-cache? freezable?] :as form-info} hash digest-file]
  (try
    (let [!interned-vars (atom #{})
          {:keys [result]} (time-ms (binding [config/*in-clerk* true]
                                      (assert form "form must be set")
                                      (with-redefs [clojure.core/intern (partial intern+record !interned-vars)]
                                        (eval form))))
          result (if (and (nil? result) var (= 'defonce (first form)))
                   (find-var var)
                   result)
          var-value (cond-> result (and var (var? result)) deref)
          var-from-def? (and var (var? result) (= var (symbol result)))
          no-cache? (or ns-effect?
                        no-cache?
                        (boolean (seq @!interned-vars))
                        config/cache-disabled?)]
      (when (and (not no-cache?)
                 (not ns-effect?)
                 freezable?
                 (cachable-value? var-value)
                 (or (not var) var-from-def?))
        (cache! digest-file var-value))
      (let [blob-id (cond no-cache? (analyzer/->hash-str var-value)
                          (fn? var-value) nil
                          :else hash)
            result (if var-from-def?
                     (var-from-def var)
                     result)]
        (cond-> (wrapped-with-metadata result blob-id)
          (seq @!interned-vars)
          (assoc :nextjournal/interned @!interned-vars))))
    (catch Throwable t
      (let [triaged (main/ex-triage (Throwable->map t))]
        (throw (ex-info (main/ex-str triaged)
                        (merge triaged (analyzer/form->ex-data form))))))))

(defn maybe-eval-viewers [{:as opts :nextjournal/keys [viewer viewers]}]
  (cond-> opts
    viewer
    (update :nextjournal/viewer eval)
    viewers
    (update :nextjournal/viewers eval)))

(defn read+eval-cached [{:as doc :keys [blob->result ->analysis-info ->hash]} codeblock]
  (let [{:keys [form vars var]} codeblock
        {:as form-info :keys [ns-effect? no-cache? freezable?]} (->analysis-info (if (seq vars) (first vars) (analyzer/->key codeblock)))
        no-cache?      (or ns-effect? no-cache?)
        hash           (when-not no-cache? (or (get ->hash (analyzer/->key codeblock))
                                               (analyzer/hash-codeblock ->hash codeblock)))
        digest-file    (when hash (->cache-file (str "@" hash)))
        cas-hash       (when (and digest-file (fs/exists? digest-file)) (slurp digest-file))
        cached-result-in-memory (get blob->result hash)
        cached-result? (and (not no-cache?)
                            (or (some? cached-result-in-memory)
                                (and cas-hash
                                     (-> cas-hash ->cache-file fs/exists?))))
        opts-from-form-meta (-> (meta form)
                                (select-keys (keys v/viewer-opts-normalization))
                                v/normalize-viewer-opts
                                maybe-eval-viewers)]
    #_(prn :cached? (cond no-cache? :no-cache
                          cached-result? (if cached-result-in-memory
                                           :in-memory
                                           :in-cas)
                          cas-hash :no-cas-file
                          :else :no-digest-file)
           :hash hash :cas-hash cas-hash :form form :var var :ns-effect? ns-effect?)
    (fs/create-dirs config/cache-dir)
    (cond-> (or (when (and cached-result? cached-result-in-memory)
                  (wrapped-with-metadata (:nextjournal/value cached-result-in-memory) hash))
                (when (and cached-result? freezable?)
                  (lookup-cached-result (session/in-session-ns doc var) hash cas-hash))
                (eval+cache! (assoc form-info :form form) hash digest-file))
      (seq opts-from-form-meta)
      (merge opts-from-form-meta))))

#_(nextjournal.clerk/show! "notebooks/exec_status.clj")

#_(eval-file "notebooks/test123.clj")
#_(eval-file "notebooks/how_clerk_works.clj")

#_(blob->result @nextjournal.clerk.webserver/!doc)



(defn ->eval-status [{:as analyzed-doc :keys [blocks]} num-done {:as block-to-eval :keys [var form]}]
  (let [total (count (filter parser/code? blocks))
        offset 0.35]
    {:progress (+ offset (* 0.6 (/ num-done total)))
     :status (format "Evaluating cell %d of %d: `%s`…"
                     (inc num-done) total (if var
                                            (str "#'" (name var))
                                            (let [code (pr-str form)
                                                  max-length 50]
                                              (if (< max-length (count code))
                                                (str (subs code 0 max-length) ",,,")
                                                code))))}))

#_(->eval-status @webserver/!doc 0 (nth (filter parser/code? (:blocks @webserver/!doc)) 0))
#_(->eval-status @webserver/!doc 1 (nth (filter parser/code? (:blocks @webserver/!doc)) 2))
#_(->eval-status @webserver/!doc 2 (nth (filter parser/code? (:blocks @webserver/!doc)) 3))

#_(nextjournal.clerk/show! "notebooks/exec_status.clj")

(defn eval-analyzed-doc [{:as analyzed-doc :keys [->hash blocks set-status-fn]}]
  (let [deref-forms (into #{} (filter analyzer/deref?) (keys ->hash))
        {:as evaluated-doc :keys [blob-ids]}
        (reduce (fn [state cell]
                  (when (and (parser/code? cell) set-status-fn)
                    (set-status-fn (->eval-status analyzed-doc (count (filter parser/code? (:blocks state))) cell)))
                  (let [state-with-deref-deps-evaluated (analyzer/hash-deref-deps state cell)
                        {:as result :nextjournal/keys [blob-id]} (when (parser/code? cell)
                                                                   (read+eval-cached state-with-deref-deps-evaluated cell))]
                    (cond-> (update state-with-deref-deps-evaluated :blocks conj (cond-> cell result (assoc :result result)))
                      blob-id (update :blob-ids conj blob-id)
                      blob-id (assoc-in [:blob->result blob-id] result))))
                (-> analyzed-doc
                    (assoc :blocks [] :blob-ids #{})
                    (update :->hash (fn [h] (apply dissoc h deref-forms))))
                blocks)]
    (doto (-> evaluated-doc
              (update :blob->result select-keys blob-ids)
              (dissoc :blob-ids)) analyzer/throw-if-dep-is-missing)))


(defn eval-in-session [{:as analyzed-doc :keys [session ns]}]
  (if session
    (let [session-ns (session/session-ns-name analyzed-doc)]
      (binding [*ns* (create-ns session-ns)]
        (doseq [var (keep resolve
                          (filter (comp #{(str (ns-name ns))} namespace)
                                  (keys (:->analysis-info analyzed-doc))))]
          (intern session-ns (-> var symbol name symbol) @var))
        (eval-analyzed-doc (-> analyzed-doc
                               (session/rewrite-ns-form session-ns)
                               (assoc :session-ns session-ns)))))
    (eval-analyzed-doc analyzed-doc)))

#_#_(:blocks (eval-in-session (assoc @(nextjournal.clerk.webserver/get-doc!) :session 'my-session-1)))

(mapv :form (:blocks @(nextjournal.clerk.webserver/get-doc!)))

(defn +eval-results
  "Evaluates the given `parsed-doc` using the `in-memory-cache` and augments it with the results."
  [in-memory-cache {:as parsed-doc :keys [set-status-fn]}]
  (when set-status-fn (set-status-fn {:progress 0.10 :status "Analyzing…"}))
  (let [{:as analyzed-doc :keys [ns session-ns]} (analyzer/build-graph
                                                  (assoc parsed-doc :blob->result in-memory-cache))]
    (binding [*ns* (or session-ns ns)]
      (-> analyzed-doc
          analyzer/hash
          eval-in-session))))

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
