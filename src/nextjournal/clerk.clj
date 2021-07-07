;; # Introducing Clerk 👋
(ns nextjournal.clerk
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [datoteka.core :as fs]
            [nextjournal.beholder :as beholder]
            [nextjournal.clerk.hashing :as hashing]
            [nextjournal.clerk.webserver :as webserver]
            [multihash.core :as multihash]
            [multihash.digest :as digest]
            [taoensso.nippy :as nippy]))

(comment
  (alter-var-root #'nippy/*freeze-serializable-allowlist* (fn [_] "allow-and-record"))
  (alter-var-root   #'nippy/*thaw-serializable-allowlist* (fn [_] "allow-and-record"))
  (nippy/get-recorded-serializable-classes))

(alter-var-root #'nippy/*thaw-serializable-allowlist* (fn [_] (conj nippy/default-thaw-serializable-allowlist "java.io.File" "clojure.lang.Var" "clojure.lang.Namespace")))
#_(-> [(clojure.java.io/file "notebooks") (find-ns 'user)] nippy/freeze nippy/thaw)

(defn ->cache-file [hash]
  (str ".cache/" hash))

(def cache-dir
  (str fs/*cwd* fs/*sep* ".cache"))

(defn add-blob [result hash]
  (cond-> result
    (instance? clojure.lang.IObj result)
    (vary-meta assoc :blob/id (cond-> hash (not (string? hash)) multihash/base58))))

#_(meta (add-blob {} "foo"))

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

(defmacro time-ms
  "Pure version of `clojure.core/time`. Returns a map with `:result` and `:time-ms` keys."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     {:result ret#
      :time-ms (/ (double (- (. System (nanoTime)) start#)) 1000000.0)}))

(defn worth-caching? [time-ms]
  (<= 1.0 time-ms))

#_(worth-caching? 0.1)


(defn read+eval-cached [results-last-run vars->hash code-string]
  (let [form (hashing/read-string code-string)
        {:as analyzed :keys [var]} (hashing/analyze form)
        hash (hashing/hash vars->hash analyzed)
        digest-file (->cache-file (str "@" hash))
        no-cache? (hashing/no-cache? form)
        cas-hash (when (fs/exists? digest-file)
                   (slurp digest-file))
        cached? (boolean (and cas-hash (-> cas-hash ->cache-file fs/exists?)))]
    (prn :cached? (cond no-cache? :no-cache
                        cached? true
                        (fs/exists? digest-file) :no-cas-file
                        :else :no-digest-file)
         :hash hash :cas-hash cas-hash :form form)
    (fs/create-dir cache-dir)
    (or (when (and (not no-cache?)
                   cached?)
          (try
            (let [value (add-blob (or (get results-last-run hash)
                                      (thaw-from-cas cas-hash)) hash)]
              (when var
                (intern *ns* (-> var symbol name symbol) value))
              value)
            (catch Exception _e
              ;; TODO better report this error, anything that can't be read shouldn't be cached in the first place
              #_(prn :thaw-error e)
              nil)))
        (let [{:keys [result time-ms]} (time-ms (eval form))
              no-cache? (or no-cache?
                            (let [no-cache? (not (worth-caching? time-ms))]
                              (when no-cache? (prn :not-worth-caching time-ms))
                              no-cache?))
              var-value (cond-> result (var? result) deref)]
          (if (fn? var-value)
            result
            (do (when-not (or no-cache?
                              (instance? clojure.lang.IDeref var-value)
                              (instance? clojure.lang.MultiFn var-value)
                              (instance? clojure.lang.Namespace var-value)
                              (and (seq? form) (contains? #{'ns 'in-ns 'require} (first form))))
                  (try
                    (spit digest-file (hash+store-in-cas! var-value))
                    (catch Exception e
                      (prn :freeze-error e)
                      nil)))
                (add-blob var-value (if no-cache? (multihash/base58 (digest/sha1 (str (java.util.UUID/randomUUID)))) hash))))))))

(defn clear-cache!
  ([]
   (let [cache-dir (str fs/*cwd* fs/*sep* ".cache")]
     (if (fs/exists? cache-dir)
       (do
         (fs/delete (str fs/*cwd* fs/*sep* ".cache"))
         (prn :cache-dir/deleted cache-dir))
       (prn :cache-dir/does-not-exist cache-dir)))))


(defn blob->result [doc]
  (into {} (comp (keep :result)
                 (filter meta)
                 (map (juxt (comp :blob/id meta) identity))) doc))

(defn +eval-results [results-last-run vars->hash doc]
  (let [doc (into [] (map (fn [{:as cell :keys [type text]}]
                            (cond-> cell
                              (= :code type)
                              (assoc :result (read+eval-cached results-last-run vars->hash text))))) doc)]
    (with-meta doc (blob->result doc))))

#_(let [doc (+eval-results {} {} [{:type :markdown :text "# Hi"} {:type :code :text "[1]"} {:type :code :text "(+ 39 3)"}])
        blob->result (meta doc)]
    (+eval-results blob->result {} doc))

(defn parse-file [file]
  (hashing/parse-file {:markdown? true} file))

#_(parse-file "notebooks/elements.clj")

(defn show!
  "Converts the Clojure source test in file to a series of text or syntax panes and causes `panel` to contain them."
  [file]
  (try
    (let [doc (parse-file file)
          results-last-run (meta @webserver/!doc)]
      ;; TODO diff to avoid flickering
      #_(webserver/update-doc! doc)
      (webserver/update-doc! (+eval-results results-last-run (hashing/hash file) doc)))
    (catch Exception e
      (webserver/show-error! e)
      (throw e))))

(defn file-event [{:keys [type path]}]
  (when (contains? #{:modify :create} type)
    (binding [*ns* (find-ns 'user)]
      (nextjournal.clerk/show! (str/replace (str path) (str fs/*cwd* fs/*sep*) "")))))

;; And, as is the culture of our people, a commend block containing
;; pieces of code with which to pilot the system during development.
(comment
  (def watcher
    (beholder/watch #(file-event %) "notebooks" "src"))

  (beholder/stop watcher)

  (show! "notebooks/elements.clj")
  (show! "notebooks/rule_30.clj")
  (show! "notebooks/onwards.clj")
  (show! "notebooks/pagination.clj")
  (show! "notebooks/how_clerk_works.clj")
  (show! "src/nextjournal/clerk/hashing.clj")
  (show! "src/nextjournal/clerk.clj")

  ;; Clear cache
  (clear-cache!)

  )
