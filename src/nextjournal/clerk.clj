;; # Introducing Clerk 👋
(ns nextjournal.clerk
  (:require [clojure.java.browse :as browse]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datoteka.core :as fs]
            [multihash.core :as multihash]
            [multihash.digest :as digest]
            [nextjournal.beholder :as beholder]
            [nextjournal.clerk.hashing :as hashing]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver]
            [taoensso.nippy :as nippy]))

(comment
  (alter-var-root #'nippy/*freeze-serializable-allowlist* (fn [_] "allow-and-record"))
  (alter-var-root   #'nippy/*thaw-serializable-allowlist* (fn [_] "allow-and-record"))
  (nippy/get-recorded-serializable-classes))

(alter-var-root #'nippy/*thaw-serializable-allowlist* (fn [_] (conj nippy/default-thaw-serializable-allowlist "java.io.File" "clojure.lang.Var" "clojure.lang.Namespace")))
#_(-> [(clojure.java.io/file "notebooks") (find-ns 'user)] nippy/freeze nippy/thaw)


(defn cache-dir []
  (or (System/getProperty "clerk.cache_dir")
      ".cache"))

(defn ->cache-file [hash]
  (str (cache-dir) fs/*sep* hash))

(defn cache-disabled? []
  (when-let [prop (System/getProperty "clerk.disable_cache")]
    (not= "false" prop)))

(defn wrap-with-blob-id [result hash]
  {:result result :blob-id (cond-> hash (not (string? hash)) multihash/base58)})

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

(defn random-multihash []
  (multihash/base58 (digest/sha1 (str (java.util.UUID/randomUUID)))))

#_(random-multihash)

(defn read+eval-cached [results-last-run vars->hash code-string]
  (let [form (hashing/read-string code-string)
        {:as analyzed :keys [var]} (hashing/analyze form)
        hash (hashing/hash vars->hash analyzed)
        digest-file (->cache-file (str "@" hash))
        no-cache? (hashing/no-cache? form)
        cas-hash (when (fs/exists? digest-file)
                   (slurp digest-file))
        cached? (boolean (and cas-hash (-> cas-hash ->cache-file fs/exists?)))]
    #_(prn :cached? (cond no-cache? :no-cache
                          cached? true
                          (fs/exists? digest-file) :no-cas-file
                          :else :no-digest-file)
           :hash hash :cas-hash cas-hash :form form)
    (fs/create-dir (cache-dir))
    (or (when (and (not no-cache?)
                   cached?)
          (try
            (let [{:as result+blob :keys [result]} (wrap-with-blob-id (or (get results-last-run hash)
                                                                          (thaw-from-cas cas-hash)) hash)]
              (when var
                (intern *ns* (-> var symbol name symbol) result))
              result+blob)
            (catch Exception _e
              ;; TODO better report this error, anything that can't be read shouldn't be cached in the first place
              #_(prn :thaw-error e)
              nil)))
        (let [{:keys [result time-ms]} (time-ms (eval form))
              no-cache? (or no-cache?
                            (cache-disabled?)
                            (let [no-cache? (not (worth-caching? time-ms))]
                              #_(when no-cache? (prn :not-worth-caching time-ms))
                              no-cache?))
              var-value (cond-> result (var? result) deref)]
          (if (fn? var-value)
            var-value
            (do (when-not (or no-cache?
                              (instance? clojure.lang.IDeref var-value)
                              (instance? clojure.lang.MultiFn var-value)
                              (instance? clojure.lang.Namespace var-value)
                              (and (seq? form) (contains? #{'ns 'in-ns 'require} (first form))))
                  (try
                    (spit digest-file (hash+store-in-cas! var-value))
                    (catch Exception e
                      #_(prn :freeze-error e)
                      nil)))
                (wrap-with-blob-id var-value (if no-cache? (random-multihash) hash))))))))

#_(read+eval-cached {} {} "(subs (slurp \"/usr/share/dict/words\") 0 1000)")

(defn clear-cache!
  ([]
   (let [cache-dir (cache-dir)]
     (if (fs/exists? cache-dir)
       (do
         (fs/delete cache-dir)
         (prn :cache-dir/deleted cache-dir))
       (prn :cache-dir/does-not-exist cache-dir)))))


(defn blob->result [doc]
  (into {} (comp (keep :result)
                 (map (juxt :blob-id :result))) doc))

#_(blob->result @nextjournal.clerk.webserver/!doc)

(defn +eval-results [results-last-run vars->hash doc]
  (let [doc (into [] (map (fn [{:as cell :keys [type text]}]
                            (cond-> cell
                              (= :code type)
                              (assoc :result (read+eval-cached results-last-run vars->hash text))))) doc)]
    (with-meta doc (-> doc blob->result (assoc :ns *ns*)))))

#_(let [doc (+eval-results {} {} [{:type :markdown :text "# Hi"} {:type :code :text "[1]"} {:type :code :text "(+ 39 3)"}])
        blob->result (meta doc)]
    (+eval-results blob->result {} doc))

(defn parse-file [file]
  (hashing/parse-file {:markdown? true} file))

#_(parse-file "notebooks/elements.clj")

(defn eval-file
  ([file] (eval-file {} file))
  ([results-last-run file]
   (+eval-results results-last-run (hashing/hash file) (parse-file file))))

#_(eval-file "notebooks/rule_30.clj")

(defn show!
  "Converts the Clojure source test in file to a series of text or syntax panes and causes `panel` to contain them."
  [file]
  (try
    (let [doc (parse-file file)
          results-last-run (meta @webserver/!doc)
          {:keys [result time-ms]} (time-ms (+eval-results results-last-run (hashing/hash file) doc))]
      ;; TODO diff to avoid flickering
      #_(webserver/update-doc! doc)
      (println (str "Clerk evaluated '" file "' in " time-ms "ms."))
      (webserver/update-doc! result))
    (catch Exception e
      (webserver/show-error! e)
      (throw e))))

(defn file-event [{:keys [type path]}]
  (when (and (contains? #{:modify :create} type)
             (or (str/ends-with? path ".clj")
                 (str/ends-with? path ".cljc")))

    (binding [*ns* (find-ns 'user)]
      (nextjournal.clerk/show! (str/replace (str path) (str fs/*cwd* fs/*sep*) "")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public viewer api
(def md             v/md)
(def plotly         v/plotly)
(def vl             v/vl)
(def tex            v/tex)
(def notebook       v/notebook)
(def html           v/html)
(def code           v/code)
(def table          v/table)

(defmacro with-viewer
  [viewer x]
  (let [viewer# (list 'quote viewer)]
    `(v/with-viewer* ~viewer# ~x)))

#_(macroexpand '(with-viewer #(v/html [:div %]) 1))

(defmacro with-viewers
  [viewers x]
  (let [viewers# (->> viewers
                      v/preds->fn+
                      (mapv (fn [viewer] (update viewer :fn #(list 'quote %)))))]
    `(v/with-viewers* ~viewers# ~x)))

#_(macroexpand '(with-viewers [{:pred number? :fn #(v/html [:div %])}] 1))


(defmacro set-viewers!
  ([viewers] (v/set-viewers!* *ns* viewers))
  ([scope viewers] (v/set-viewers!* scope viewers)))

#_(set-viewers! [])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; static builds

(defn file->viewer
  "Evaluates the given `file` and returns it's viewer representation."
  ([file] (file->viewer {:inline-results? true} file))
  ([opts file] (view/doc->viewer opts (eval-file file))))

#_(file->viewer "notebooks/rule_30.clj")

(def clerk-docs
  (into []
        (map #(str "notebooks/" % ".clj"))
        ["hello"
         "rule_30"
         "onwards"
         "viewers/html"
         "viewers/markdown"
         "viewers/plotly"
         "viewers/table"
         "viewers/vega"]))


(defn build-static-app!
  "Builds a static html app of the notebooks at `paths`."
  [{:keys [paths out-path]
    :or {paths clerk-docs
         out-path "public/build"}}]
  (let [docs (into {} (map (fn [path] {path (file->viewer path)}) paths))
        out-html (str out-path fs/*sep* "index.html")]
    (fs/create-dir (fs/parent out-html))
    (spit out-html (view/->static-app {:live-js? false} docs))
    (browse/browse-url out-html)))

#_(build-static-app! {})

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
  (show! "notebooks/conditional_read.cljc")
  (show! "src/nextjournal/clerk/hashing.clj")
  (show! "src/nextjournal/clerk.clj")

  (show! "notebooks/test.clj")

  ;; Clear cache
  (clear-cache!)

  )
