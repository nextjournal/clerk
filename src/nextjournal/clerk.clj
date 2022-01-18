;; # Introducing Clerk 👋
(ns nextjournal.clerk
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [multihash.core :as multihash]
            [multihash.digest :as digest]
            [nextjournal.beholder :as beholder]
            [nextjournal.clerk.config :as config]
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


(defn ->cache-file [hash]
  (str (config/cache-dir) fs/file-separator hash))

(defn wrapped-with-metadata [value visibility hash]
  (cond-> {:nextjournal/value value
           ::visibility visibility}
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

(defmacro time-ms
  "Pure version of `clojure.core/time`. Returns a map with `:result` and `:time-ms` keys."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     {:result ret#
      :time-ms (/ (double (- (. System (nanoTime)) start#)) 1000000.0)}))

(defn- var-from-def [var]
  (let [resolved-var (cond (var? var)
                           var

                           (symbol? var)
                           (find-var var)

                           :else
                           (throw (ex-info "Unable to resolve into a variable" {:data var})))]
    {::var-from-def resolved-var}))

(defn- lookup-cached-result [results-last-run introduced-var hash cas-hash visibility]
  (try
    (let [value (or (get results-last-run hash)
                    (let [cached-value (thaw-from-cas cas-hash)]
                      (when introduced-var
                        (intern *ns* (-> introduced-var symbol name symbol) cached-value))
                      cached-value))]
      (wrapped-with-metadata (if introduced-var (var-from-def introduced-var) value) visibility hash))
    (catch Exception _e
      ;; TODO better report this error, anything that can't be read shouldn't be cached in the first place
      #_(prn :thaw-error e)
      nil)))

(defn- cachable-value? [value]
  (not (or (fn? value)
           (instance? clojure.lang.IDeref value)
           (instance? clojure.lang.MultiFn value)
           (instance? clojure.lang.Namespace value))))

(defn- cache! [digest-file var-value]
  (try
    (spit digest-file (hash+store-in-cas! var-value))
    (catch Exception _e
      #_(prn :freeze-error e)
      nil)))

(defn- eval+cache! [form hash digest-file introduced-var no-cache? visibility]
  (let [{:keys [result]} (time-ms (binding [config/*in-clerk* true] (eval form)))
        var-value (cond-> result (var? result) deref)
        no-cache? (or no-cache?
                      (config/cache-disabled?)
                      (view/exceeds-bounded-count-limit? var-value))]
    (when (and (not no-cache?) (cachable-value? var-value))
      (cache! digest-file var-value))
    (let [blob-id (cond no-cache? (view/->hash-str var-value)
                        (fn? var-value) nil
                        :else hash)]
      (wrapped-with-metadata (cond-> result introduced-var var-from-def) visibility blob-id))))

(defn- ensure-cache-dir! []
  (let [cache-dir (config/cache-dir)]
    (when-not (fs/exists? cache-dir)
      (fs/create-dirs cache-dir))))

(defn read+eval-cached [results-last-run vars->hash doc-visibility code-string]
  (let [form           (hashing/read-string code-string)
        analyzed       (hashing/analyze form)
        hash           (hashing/hash vars->hash analyzed)
        digest-file    (->cache-file (str "@" hash))
        no-cache?      (or (:ns-effect? analyzed)
                           (hashing/no-cache? form))
        cas-hash       (when (fs/exists? digest-file)
                         (slurp digest-file))
        visibility     (if-let [fv (hashing/->visibility form)] fv doc-visibility)
        cached-result? (and (not no-cache?)
                            cas-hash
                            (-> cas-hash ->cache-file fs/exists?))]
    #_(prn :cached? (cond no-cache? :no-cache
                          cached-result? true
                          (fs/exists? digest-file) :no-cas-file
                          :else :no-digest-file)
           :hash hash :cas-hash cas-hash :form form)
    (ensure-cache-dir!)
    (let [introduced-var (:var analyzed)]
      (or (when cached-result?
            (lookup-cached-result results-last-run introduced-var hash cas-hash visibility))
          (eval+cache! form hash digest-file introduced-var no-cache? visibility)))))

#_(read+eval-cached {} {} #{:show} "(subs (slurp \"/usr/share/dict/words\") 0 1000)")

(defn clear-cache!
  ([]
   (let [cache-dir (config/cache-dir)]
     (if (fs/exists? cache-dir)
       (do
         (fs/delete-tree cache-dir)
         (prn :cache-dir/deleted cache-dir))
       (prn :cache-dir/does-not-exist cache-dir)))))


(defn blob->result [doc]
  (into {} (comp (keep :result)
                 (map (juxt :nextjournal/blob-id :nextjournal/value))) doc))

#_(blob->result @nextjournal.clerk.webserver/!doc)

(defn +eval-results [results-last-run vars->hash {:keys [doc visibility]}]
  (let [doc (into [] (map (fn [{:as cell :keys [type text]}]
                            (cond-> cell
                              (= :code type)
                              (assoc :result (read+eval-cached results-last-run vars->hash visibility text))))) doc)]
    (with-meta doc (-> doc blob->result (assoc :ns *ns*)))))

#_(let [doc (+eval-results {} {} [{:type :markdown :text "# Hi"} {:type :code :text "[1]"} {:type :code :text "(+ 39 3)"}])
        blob->result (meta doc)]
    (+eval-results blob->result {} doc))

(defn parse-file [file]
  (hashing/parse-file {:markdown? true} file))

#_(parse-file "notebooks/elements.clj")
#_(parse-file "notebooks/visibility.clj")

(defn eval-file
  ([file] (eval-file {} file))
  ([results-last-run file]
   (+eval-results results-last-run (hashing/hash file) (parse-file file))))

#_(eval-file "notebooks/rule_30.clj")
#_(eval-file "notebooks/visibility.clj")

(defonce !show-filter-fn (atom nil))
(defonce !last-file (atom nil))
(defonce !watcher (atom nil))

(defn show!
  "Evaluates the Clojure source in `file` and makes the webserver show it."
  [file]
  (if config/*in-clerk*
    ::ignored
    (try
      (reset! !last-file file)
      (let [doc (parse-file file)
            results-last-run (meta @webserver/!doc)
            {:keys [result time-ms]} (time-ms (+eval-results results-last-run (hashing/hash doc) doc))]
        ;; TODO diff to avoid flickering
        #_(webserver/update-doc! doc)
        (println (str "Clerk evaluated '" file "' in " time-ms "ms."))
        (webserver/update-doc! result))
      (catch Exception e
        (webserver/show-error! e)
        (throw e)))))

(defn supported-file?
  "Returns whether `path` points to a file that should be shown."
  [path]
  ;; file names starting with .# are most likely Emacs lock files and should be ignored.
  (some? (re-matches #"(?!^\.#).+\.(md|clj|cljc)$" (.. path getFileName toString))))

#_(supported-file? (fs/path "foo_bar.clj"))
#_(supported-file? (fs/path "xyz/foo.md"))
#_(supported-file? (fs/path "xyz/foo.clj"))
#_(supported-file? (fs/path "xyz/a.#name.cljc"))
#_(supported-file? (fs/path ".#name.clj"))
#_(supported-file? (fs/path "xyz/.#name.cljc"))


(defn file-event [{:keys [type path]}]
  (when (and (contains? #{:modify :create} type)
             (supported-file? path))
    (binding [*ns* (find-ns 'user)]
      (let [rel-path (str/replace (str path) (str (fs/canonicalize ".") fs/file-separator) "")
            show-file? (or (not @!show-filter-fn)
                           (@!show-filter-fn rel-path))]
        (cond
          show-file? (nextjournal.clerk/show! rel-path)
          @!last-file (nextjournal.clerk/show! @!last-file))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public viewer api
(def md             v/md)
(def plotly         v/plotly)
(def vl             v/vl)
(def tex            v/tex)
(def notebook       v/notebook)
(def html           v/html)
(def code           v/code)
(def table          #'v/table)
(def use-headers    #'v/use-headers)
(def hide-result    #'v/hide-result)
(def doc-url        #'v/doc-url)
(def with-viewer    #'v/with-viewer)
(def with-viewers   #'v/with-viewers)
(def set-viewers!   #'v/set-viewers!)

(defn file->viewer
  "Evaluates the given `file` and returns it's viewer representation."
  ([file] (file->viewer {:inline-results? true} file))
  ([opts file] (view/doc->viewer opts (eval-file file))))

#_(file->viewer "notebooks/rule_30.clj")

(defn- halt-watcher! []
  (when-let [{:keys [watcher paths]} @!watcher]
    (println "Stopping old watcher for paths" (pr-str paths))
    (beholder/stop watcher)
    (reset! !watcher nil)))

(defn serve!
  "Main entrypoint to Clerk taking an configurations map.

  Will obey the following optional configuration entries:

  * a `:port` for the webserver to listen on, defaulting to `7777`
  * `:browse?` will open Clerk in a browser after it's been started
  * a sequence of `:watch-paths` that Clerk will watch for file system events and show any changed file
  * a `:show-filter-fn` to restrict when to re-evaluate or show a notebook as a result of file system event. Useful for e.g. pinning a notebook. Will be called with the string path of the changed file.

  Can be called multiple times and Clerk will happily serve you according to the latest config."
  [{:as config
    :keys [browse? watch-paths port show-filter-fn]
    :or {port 7777}}]
  (webserver/serve! {:port port})
  (reset! !show-filter-fn show-filter-fn)
  (halt-watcher!)
  (when (seq watch-paths)
    (println "Starting new watcher for paths" (pr-str watch-paths))
    (reset! !watcher {:paths watch-paths
                      :watcher (apply beholder/watch #(file-event %) watch-paths)}))
  (when browse?
    (browse/browse-url (str "http://localhost:" port)))
  config)

(defn halt!
  "Stops the Clerk webserver and file watcher"
  []
  (webserver/halt!)
  (halt-watcher!))

#_(serve! {})
#_(serve! {:browse? true})
#_(serve! {:watch-paths ["src" "notebooks"]})
#_(serve! {:watch-paths ["src" "notebooks"] :show-filter-fn #(clojure.string/starts-with? % "notebooks")})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; static builds

(def clerk-docs
  (into ["notebooks/markdown.md"
         "notebooks/onwards.md"]
        (map #(str "notebooks/" % ".clj"))
        ["hello"
         "how_clerk_works"
         "pagination"
         "paren_soup"
         #_"readme" ;; TODO: add back when we have Clojure cells in md
         "rule_30"
         "visibility"
         "viewer_api"
         "viewers/html"
         "viewers/image"
         "viewers/markdown"
         "viewers/plotly"
         "viewers/table"
         "viewers/tex"
         "viewers/vega"]))


(defn strip-index [path]
  (str/replace path #"(^|.*/)(index\.(clj|cljc|md))$" "$1"))

#_(strip-index "index.md")
#_(strip-index "index.cljc")
#_(strip-index "hello/index.cljc")
#_(strip-index "hello_index.cljc")

(defn ->html-extension [path]
  (str/replace path #"\.(clj|cljc|md)$" ".html"))

#_(->html-extension "hello.clj")


(defn- path-to-url-canonicalize
  "Canonicalizes the system specific path separators in `PATH` (e.g. `\\`
  on MS-Windows) to URL-compatible forward slashes."
  [path]
  (str/replace path fs/file-separator "/"))

(defn build-static-app!
  "Builds a static html app of the notebooks and opens the app in the
  default browser. Takes an options map with keys:

  - `:paths` a vector of relative paths to notebooks to include in the build
  - `:bundle?` builds a single page app versus a folder with an html page for each notebook (defaults to `true`)
  - `:path-prefix` a prefix to urls
  - `:out-path` a relative path to a folder to contain the static pages (defaults to `\"public/build\"`)
  - `:git/sha`, `:git/url` when both present, each page displays a link to `(str url \"blob\" sha path-to-notebook)`
  "
  [{:as opts :keys [paths out-path bundle? browse?]
    :or {paths clerk-docs
         out-path (str "public" fs/file-separator "build")
         bundle? true
         browse? true}}]
  (let [path->doc (into {} (map (juxt identity file->viewer)) paths)
        path->url (into {} (map (juxt identity #(cond-> (strip-index %) (not bundle?) ->html-extension))) paths)
        static-app-opts (assoc opts :bundle? bundle? :path->doc path->doc :paths (vec (keys path->doc)) :path->url path->url)
        index-html (str out-path fs/file-separator "index.html")]
    (when-not (fs/exists? (fs/parent index-html))
      (fs/create-dirs (fs/parent index-html)))
    (if bundle?
      (spit index-html (view/->static-app static-app-opts))
      (do (when-not (contains? (-> path->url vals set) "") ;; no user-defined index page
            (spit index-html (view/->static-app (dissoc static-app-opts :path->doc))))
          (doseq [[path doc] path->doc]
            (let [out-html (str out-path fs/file-separator (str/replace path #"(.clj|.md)" ".html"))]
              (fs/create-dirs (fs/parent out-html))
              (spit out-html (view/->static-app (assoc static-app-opts :path->doc (hash-map path doc) :current-path path)))))))
    (when browse?
      (if (str/starts-with? out-path "public/")
        (browse/browse-url (str "http://localhost:7778/" (str/replace out-path "public/" "")))
        (browse/browse-url (-> index-html fs/absolutize .toString path-to-url-canonicalize))))))

#_(build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/markdown.md"] :bundle? true})
#_(build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/markdown.md"] :bundle? false :path-prefix "build/"})
#_(build-static-app! {})
#_(build-static-app! {:paths ["notebooks/viewer_api.clj" "notebooks/rule_30.clj"]})

;; And, as is the culture of our people, a commend block containing
;; pieces of code with which to pilot the system during development.
(comment
  (def watcher
    (beholder/watch #(file-event %) "notebooks" "src"))

  (beholder/stop watcher)

  (show! "notebooks/rule_30.clj")
  (show! "notebooks/viewer_api.clj")
  (show! "notebooks/onwards.md")
  (show! "notebooks/pagination.clj")
  (show! "notebooks/how_clerk_works.clj")
  (show! "notebooks/conditional_read.cljc")
  (show! "src/nextjournal/clerk/hashing.clj")
  (show! "src/nextjournal/clerk.clj")

  (show! "notebooks/test.clj")

  ;; Clear cache
  (clear-cache!)

  )
