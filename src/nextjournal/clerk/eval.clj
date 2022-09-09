(ns nextjournal.clerk.eval
  "Clerk's incremental evaluation with in-memory and disk-persisted caching layers."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [multihash.core :as multihash]
            [multihash.digest :as digest]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.parser :as parser]
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
    hash (assoc :nextjournal/blob-id (cond-> hash (not (string? hash)) multihash/base58))))

#_(wrap-with-blob-id :test "foo")

(defn hash+store-in-cas! [x]
  (let [^bytes ba (nippy/freeze x)
        multihash (multihash/base58 (digest/sha2-512 ba))
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
  (try
    (let [value (let [cached-value (thaw-from-cas cas-hash)]
                  (when introduced-var
                    (intern (-> introduced-var symbol namespace find-ns) (-> introduced-var symbol name symbol) cached-value))
                  cached-value)]
      (wrapped-with-metadata (if introduced-var (var-from-def introduced-var) value) hash))
    (catch Exception _e
      ;; TODO better report this error, anything that can't be read shouldn't be cached in the first place
      #_(prn :thaw-error e)
      nil)))

(defn ^:private cachable-value? [value]
  (and (some? value)
       (try
         (nippy/freezable? value)
         ;; can error on e.g. lazy-cat fib
         ;; TODO: propagate error information here
         (catch Exception _
           false))
       (not (analyzer/exceeds-bounded-count-limit? value))))

#_(cachable-value? (vec (range 100)))
#_(cachable-value? (range))


(defn ^:private cache! [digest-file var-value]
  (try
    (spit digest-file (hash+store-in-cas! var-value))
    (catch Exception e
      #_(prn :freeze-error e)
      nil)))

(defn ^:private eval+cache! [{:keys [form var ns-effect? no-cache? freezable?] :as form-info} hash digest-file form-line-number]
  (try
    (let [{:keys [result]} (time-ms (binding [config/*in-clerk* true]
                                      (eval form)))
          result (if (and (nil? result) var (= 'defonce (first form)))
                   (find-var var)
                   result)
          var-value (cond-> result (and var (var? result)) deref)
          no-cache? (or ns-effect?
                        no-cache?
                        config/cache-disabled?)]
      (when (and (not no-cache?) (not ns-effect?) freezable? (cachable-value? var-value))
        (cache! digest-file var-value))
      (let [blob-id (cond no-cache? (analyzer/->hash-str var-value)
                          (fn? var-value) nil
                          :else hash)
            result (if var
                     (var-from-def var)
                     result)]
        (wrapped-with-metadata result blob-id)))
    (catch Exception e
      (throw (ex-info (ex-message e)
                      (-> form-info
                          (select-keys [:file :var :form])
                          (assoc :line form-line-number))
                      e)))))

(defn maybe-eval-viewers [{:as opts :nextjournal/keys [viewer viewers]}]
  (cond-> opts
    viewer
    (update :nextjournal/viewer eval)
    viewers
    (update :nextjournal/viewers eval)))

(defn read+eval-cached [{:as _doc :keys [blob->result ->analysis-info ->hash]} codeblock]
  (let [{:keys [form vars var deref-deps]} codeblock
        {:as form-info :keys [ns-effect? no-cache? freezable?]} (->analysis-info (if (seq vars) (first vars) form))
        no-cache?      (or ns-effect? no-cache?)
        hash           (when-not no-cache? (or (get ->hash (if var var form))
                                               (analyzer/hash-codeblock ->hash codeblock)))
        digest-file    (when hash (->cache-file (str "@" hash)))
        cas-hash       (when (and digest-file (fs/exists? digest-file)) (slurp digest-file))
        cached-result? (and (not no-cache?)
                            cas-hash
                            (-> cas-hash ->cache-file fs/exists?))
        opts-from-form-meta (-> (meta form)
                                (select-keys [:nextjournal.clerk/viewer :nextjournal.clerk/viewers :nextjournal.clerk/width :nextjournal.clerk/opts])
                                v/normalize-viewer-opts
                                maybe-eval-viewers)]
    #_(prn :cached? (cond no-cache? :no-cache
                          cached-result? true
                          cas-hash :no-cas-file
                          :else :no-digest-file)
           :hash hash :cas-hash cas-hash :form form :var var :ns-effect? ns-effect?)
    (fs/create-dirs config/cache-dir)
    (cond-> (or (when-let [blob->result (and (not no-cache?) (get-in blob->result [hash :nextjournal/value]))]
                  (wrapped-with-metadata blob->result hash))
                (when (and cached-result? freezable?)
                  (lookup-cached-result var hash cas-hash))
                (eval+cache! form-info hash digest-file (-> codeblock :meta :row)))
      (seq opts-from-form-meta)
      (merge opts-from-form-meta))))

#_(show! "notebooks/scratch_cache.clj")

#_(eval-file "notebooks/test123.clj")
#_(eval-file "notebooks/how_clerk_works.clj")

#_(blob->result @nextjournal.clerk.webserver/!doc)

(defn eval-analyzed-doc [{:as analyzed-doc :keys [->hash blocks]}]
  (let [deref-forms (into #{} (filter analyzer/deref?) (keys ->hash))
        {:as evaluated-doc :keys [blob-ids]}
        (reduce (fn [{:as state :keys [blob->result]} {:as cell :keys [type]}]
                  (let [state-with-deref-deps-evaluated (analyzer/hash-deref-deps state cell)
                        {:as result :nextjournal/keys [blob-id]} (when (= :code type)
                                                                   (read+eval-cached state-with-deref-deps-evaluated cell))]
                    (cond-> (update state-with-deref-deps-evaluated :blocks conj (cond-> cell result (assoc :result result)))
                      blob-id (update :blob-ids conj blob-id)
                      blob-id (assoc-in [:blob->result blob-id] result))))
                (-> analyzed-doc
                    (assoc :blocks [] :blob-ids #{})
                    (update :->hash (fn [h] (apply dissoc h deref-forms))))
                blocks)]
    (-> evaluated-doc
        (update :blob->result select-keys blob-ids)
        (dissoc :blob-ids))))

(defn +eval-results
  "Evaluates the given `parsed-doc` using the `in-memory-cache` and augments it with the results."
  [in-memory-cache parsed-doc]
  (let [{:as analyzed-doc :keys [ns]} (analyzer/build-graph parsed-doc)]
    (binding [*ns* ns]
      (-> analyzed-doc
          analyzer/hash
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

