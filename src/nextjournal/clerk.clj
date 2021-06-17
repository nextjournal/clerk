;; # Introducing Clerk 👋
(ns nextjournal.clerk
  (:require [clojure.string :as str]
            [datoteka.core :as fs]
            [nextjournal.beholder :as beholder]
            [nextjournal.clerk.hashing :as hashing]
            [nextjournal.clerk.webserver :as webserver]
            [taoensso.nippy :as nippy]))


(defn add-blob
  "Tries to add the blob id to metadata of a given result."
  [result id]
  (cond-> result
    (instance? clojure.lang.IObj result)
    (vary-meta assoc :blob/id id)))

(comment
  (alter-var-root #'nippy/*freeze-serializable-allowlist* (fn [_] "allow-and-record"))
  (alter-var-root   #'nippy/*thaw-serializable-allowlist* (fn [_] "allow-and-record"))
  (nippy/get-recorded-serializable-classes))

(alter-var-root #'nippy/*thaw-serializable-allowlist* (conj nippy/default-thaw-serializable-allowlist "java.io.File"))
#_(-> [(clojure.java.io/file "notebooks")] nippy/freeze nippy/thaw)

(defn read+eval-cached [vars->hash code-string]
  (let [cache-dir (str fs/*cwd* fs/*sep* ".cache")
        form (read-string code-string)
        {:as analyzed-form :keys [var]} (hashing/analyze form)
        hash (hashing/hash vars->hash analyzed-form)
        cache-file (str cache-dir fs/*sep* hash)
        no-cache? (hashing/no-cache? form)]
    (prn :hash hash :eval form :cached? (boolean (and (not no-cache?) (fs/exists? cache-file))))
    (fs/create-dir cache-dir)
    (or (when (and (not no-cache?)
                   (fs/exists? cache-file))
          (try
            (let [value (add-blob (nippy/thaw-from-file cache-file) hash)]
              (when var
                (intern *ns* (-> var symbol name symbol) value))
              value)
            (catch Exception e
              ;; TODO better report this error, anything that can't be read shouldn't be cached in the first place
              (prn :read-error e)
              nil)))
        (let [result (eval form)
              var-value (cond-> result (var? result) deref)]
          (if (fn? var-value)
            result
            (do (when-not (or no-cache?
                              (instance? clojure.lang.IDeref var-value)
                              (instance? clojure.lang.MultiFn var-value)
                              (instance? clojure.lang.Namespace var-value)
                              (and (seq? form) (contains? #{'ns 'in-ns 'require} (first form))))
                  (nippy/freeze-to-file cache-file var-value))
                (add-blob var-value hash)))))))

(defn clear-cache!
  ([]
   (let [cache-dir (str fs/*cwd* fs/*sep* ".cache")]
     (when (fs/exists? cache-dir)
       (fs/delete (str fs/*cwd* fs/*sep* ".cache"))))))


(defn blob->result [doc]
  (into {} (comp (keep :result)
                 (filter meta)
                 (map (juxt (comp :blob/id meta) identity))) doc))

(defn +eval-results [vars->hash doc]
  (let [doc (into [] (map (fn [{:as cell :keys [type text]}]
                            (cond-> cell
                              (= :code type)
                              (assoc :result (read+eval-cached vars->hash text))))) doc)]
    (with-meta doc (blob->result doc))))

#_(meta (+eval-results {} [{:type :markdown :text "# Hi"} {:type :code :text "[1]"} {:type :code :text "(+ 39 3)"}]))

(defn parse-file [file]
  (hashing/parse-file {:markdown? true} file))

#_(parse-file "notebooks/elements.clj")

(defn eval-file [file]
  (->> file
       parse-file
       (+eval-results (hashing/hash file))))

#_(eval-file "notebooks/elements.clj")

(defn show!
  "Converts the Clojure source test in file to a series of text or syntax panes and causes `panel` to contain them."
  [file]
  (try
    (let [doc (parse-file file)]
      ;; TODO diff to avoid flickering
      #_(webserver/update-doc! doc)
      (webserver/update-doc! (+eval-results (hashing/hash file) doc)))
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
  (show! "notebooks/how_clerk_works.clj")
  (show! "src/nextjournal/clerk/hashing.clj")
  (show! "src/nextjournal/clerk/core.clj")

  ;; Clear cache
  (clear-cache!)

  )
