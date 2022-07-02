(ns nextjournal.clerk.eval
  "Clerk's incremental evaluation with in-memory and disk-persisted caching layers."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [multihash.core :as multihash]
            [multihash.digest :as digest]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.hashing :as hashing]
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

(defn wrapped-with-metadata [value visibility hash]
  (cond-> {:nextjournal/value value
           :nextjournal.clerk/visibility visibility}
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

(defn- var-from-def [var]
  (let [resolved-var (cond (var? var)
                           var

                           (symbol? var)
                           (find-var var)

                           :else
                           (throw (ex-info "Unable to resolve into a variable" {:data var})))]
    {:nextjournal.clerk/var-from-def resolved-var}))

(defn- lookup-cached-result [introduced-var hash cas-hash visibility]
  (try
    (let [value (let [cached-value (thaw-from-cas cas-hash)]
                  (when introduced-var
                    (intern (-> introduced-var symbol namespace find-ns) (-> introduced-var symbol name symbol) cached-value))
                  cached-value)]
      (wrapped-with-metadata (if introduced-var (var-from-def introduced-var) value) visibility hash))
    (catch Exception _e
      ;; TODO better report this error, anything that can't be read shouldn't be cached in the first place
      #_(prn :thaw-error e)
      nil)))

(defn- cachable-value? [value]
  (not (or (nil? value)
           (fn? value)
           (var? value)
           (instance? clojure.lang.IDeref value)
           (instance? clojure.lang.MultiFn value)
           (instance? clojure.lang.Namespace value))))

(defn- cache! [digest-file var-value]
  (try
    (spit digest-file (hash+store-in-cas! var-value))
    (catch Exception e
      #_(prn :freeze-error e)
      nil)))

(defn- eval+cache! [{:keys [form var ns-effect? no-cache? freezable?]} hash digest-file visibility]
  (let [{:keys [result]} (time-ms (binding [config/*in-clerk* true] (eval form)))
        result (if (and (nil? result) var (= 'defonce (first form)))
                 (find-var var)
                 result)
        var-value (cond-> result (and var (var? result)) deref)
        no-cache? (or ns-effect?
                      no-cache?
                      config/cache-disabled?
                      (hashing/exceeds-bounded-count-limit? var-value))]
    (when (and (not no-cache?) (not ns-effect?) freezable? (cachable-value? var-value))
      (cache! digest-file var-value))
    (let [blob-id (cond no-cache? (hashing/->hash-str var-value)
                        (fn? var-value) nil
                        :else hash)
          result (if var
                   (var-from-def var)
                   result)]
      (wrapped-with-metadata result visibility blob-id))))

(defn maybe-eval-viewers [{:as opts :nextjournal/keys [viewer viewers]}]
  (cond-> opts
    viewer
    (update :nextjournal/viewer eval)
    viewers
    (update :nextjournal/viewers eval)))

(defn read+eval-cached [{:as _doc doc-visibility :visibility :keys [blob->result ->analysis-info ->hash]} codeblock]
  (let [{:keys [form vars var deref-deps]} codeblock
        {:as form-info :keys [ns-effect? no-cache? freezable?]} (->analysis-info (if (seq vars) (first vars) form))
        no-cache?      (or ns-effect? no-cache?)
        hash           (when-not no-cache? (or (get ->hash (if var var form))
                                               (hashing/hash-codeblock ->hash codeblock)))
        digest-file    (when hash (->cache-file (str "@" hash)))
        cas-hash       (when (and digest-file (fs/exists? digest-file)) (slurp digest-file))
        visibility     (if-let [fv (parser/->visibility form)] fv doc-visibility)
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
                  (wrapped-with-metadata blob->result visibility hash))
                (when (and cached-result? freezable?)
                  (lookup-cached-result var hash cas-hash visibility))
                (eval+cache! form-info hash digest-file visibility))
      (seq opts-from-form-meta)
      (merge opts-from-form-meta))))

#_(show! "notebooks/scratch_cache.clj")

#_(eval-file "notebooks/test123.clj")
#_(eval-file "notebooks/how_clerk_works.clj")

#_(blob->result @nextjournal.clerk.webserver/!doc)

(defn eval-analyzed-doc [{:as analyzed-doc :keys [->hash blocks visibility]}]
  (let [deref-forms (into #{} (filter hashing/deref?) (keys ->hash))
        {:as evaluated-doc :keys [blob-ids]}
        (reduce (fn [{:as state :keys [blob->result]} {:as cell :keys [type]}]
                  (let [state-with-deref-deps-evaluated (hashing/hash-deref-deps state cell)
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

(defn +eval-results [results-last-run parsed-doc]
  (let [{:as analyzed-doc :keys [ns]} (hashing/build-graph parsed-doc)]
    (binding [*ns* ns]
      (-> analyzed-doc
          hashing/hash
          (assoc :blob->result results-last-run)
          eval-analyzed-doc))))

(defn eval-doc
  ([doc] (eval-doc {} doc))
  ([results-last-run doc] (+eval-results results-last-run doc)))

(defn eval-file
  ([file] (eval-file {} file))
  ([results-last-run file]
   (->> file
        (parser/parse-file {:doc? true})
        (eval-doc results-last-run))))

#_(eval-file "notebooks/hello.clj")
#_(eval-file "notebooks/rule_30.clj")
#_(eval-file "notebooks/visibility.clj")

(defn eval-string
  ([s] (eval-string {} s))
  ([results-last-run s]
   (eval-doc results-last-run (parser/parse-clojure-string {:doc? true} s))))

#_(eval-string "(+ 39 3)")
