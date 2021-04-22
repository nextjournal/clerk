;; # Hello observator!!! 👋
(ns observator.core
  (:require [clojure.string :as str]
            [datoteka.core :as fs]
            [nextjournal.beholder :as beholder]
            [observator.hashing :as hashing]
            [observator.swing :as swing]
            [observator.webview :as webview]))

(defn read+eval-cached [vars->hash code-string]
  (let [cache-dir (str fs/*cwd* fs/*sep* ".cache")
        form (read-string code-string)
        hash (hashing/hash vars->hash (hashing/analyze form))
        cache-file (str cache-dir fs/*sep* hash)
        no-cache? (hashing/no-cache? form)]
    (fs/create-dir cache-dir)
    (if (and (not no-cache?) (fs/exists? cache-file))
      (read-string (slurp cache-file))
      (let [result (eval form)
            var-value (cond-> result (var? result) deref)]
        (if (fn? var-value)
          result
          (do (when-not (or no-cache?
                            (instance? clojure.lang.IDeref var-value)
                            (contains? #{'ns 'in-ns 'require} (first form)))
                (spit cache-file (binding [*print-meta* true] (pr-str var-value))))
              var-value))))))

(defn clear-cache!
  ([]
   (let [cache-dir (str fs/*cwd* fs/*sep* ".cache")]
     (when (fs/exists? cache-dir)
       (fs/delete (str fs/*cwd* fs/*sep* ".cache"))))))


(defn +eval-results [vars->hash doc]
  (into []
        (map (fn [{:as cell :keys [type text]}]
               (cond-> cell
                 (= :code type)
                 (assoc :result (read+eval-cached vars->hash text)))))
        doc))

#_(+eval-results {} [{:type :markdown :text "# Hi"} {:type :code :text "(+ 39 3)"}])

(defn eval-file [file]
  (->> file
       (hashing/parse-file {:markdown? true})
       (+eval-results (hashing/hash file))))

#_(eval-file "src/observator/demo.clj")

(defn show-file!
  "Converts the Clojure source test in file to a series of text or syntax panes and causes `panel` to contain them."
  [file]
  (doto (eval-file file)
    swing/show-doc!
    webview/show-doc!)
  :done)

(defn file-event [{:keys [type path]}]
  (when-let [ns-part (and (= type :modify)
                          (second (re-find #".*/src/(.*)\.clj" (str path))))]
    (binding [*ns* (find-ns (symbol (str/replace ns-part fs/*sep* ".")))]
      (observator.core/show-file! (str path)))))


;; And, as is the culture of our people, a commend block containing
;; pieces of code with which to pilot the system during development.
(comment
  (def watcher
    (beholder/watch #(file-event %) "src"))

  (beholder/stop watcher)

  (show-file! "src/observator/demo.clj")
  (show-file! "src/observator/hashing.clj")
  (show-file! "src/observator/core.clj")

  ;; Clear cache
  (clear-cache!)

  )
