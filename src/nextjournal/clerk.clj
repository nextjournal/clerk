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
    {::var-from-def resolved-var}))

(defn- lookup-cached-result [introduced-var hash cas-hash visibility]
  (try
    (let [value (let [cached-value (thaw-from-cas cas-hash)]
                  (when introduced-var
                    (intern *ns* (-> introduced-var symbol name symbol) cached-value))
                  cached-value)]
      (wrapped-with-metadata (if introduced-var (var-from-def introduced-var) value) visibility hash))
    (catch Exception _e
      ;; TODO better report this error, anything that can't be read shouldn't be cached in the first place
      #_(prn :thaw-error e)
      nil)))

(defn- cachable-value? [value]
  (not (or (nil? value)
           (fn? value)
           (instance? clojure.lang.IDeref value)
           (instance? clojure.lang.MultiFn value)
           (instance? clojure.lang.Namespace value))))

(defn- cache! [digest-file var-value]
  (try
    (spit digest-file (hash+store-in-cas! var-value))
    (catch Exception e
      #_(prn :freeze-error e)
      nil)))

(defn- eval+cache! [form hash digest-file introduced-var no-cache? visibility]
  (let [{:keys [result]} (time-ms (binding [config/*in-clerk* true] (eval form)))
        result (if (and (nil? result) introduced-var (= 'defonce (first form)))
                 (find-var introduced-var)
                 result)
        var-value (cond-> result (var? result) deref)
        no-cache? (or no-cache?
                      config/cache-disabled?
                      (hashing/exceeds-bounded-count-limit? var-value))]
    (when (and (not no-cache?) (cachable-value? var-value))
      (cache! digest-file var-value))
    (let [blob-id (cond no-cache? (hashing/->hash-str var-value)
                        (fn? var-value) nil
                        :else hash)
          result (if introduced-var
                   (var-from-def introduced-var)
                   result)]
      (wrapped-with-metadata result visibility blob-id))))

(defn maybe-eval-viewers [{:as opts :nextjournal/keys [viewer viewers]}]
  (cond-> opts
    viewer
    (update :nextjournal/viewer eval)
    viewers
    (update :nextjournal/viewers eval)))

(defn read+eval-cached [results-last-run ->hash doc-visibility codeblock]
  (let [{:keys [ns-effect? form var]} codeblock
        no-cache?      (or ns-effect?
                           (hashing/no-cache? form))
        hash           (when-not no-cache? (or (get ->hash (if var var form))
                                               (hashing/hash-codeblock ->hash codeblock)))
        digest-file    (when hash (->cache-file (str "@" hash)))
        cas-hash       (when (and digest-file (fs/exists? digest-file)) (slurp digest-file))
        visibility     (if-let [fv (hashing/->visibility form)] fv doc-visibility)
        cached-result? (and (not no-cache?)
                            cas-hash
                            (-> cas-hash ->cache-file fs/exists?))
        opts-from-form-meta (-> (meta form)
                                (select-keys [::viewer ::viewers ::width])
                                v/normalize-viewer-opts
                                maybe-eval-viewers)]
    #_(prn :cached? (cond no-cache? :no-cache
                          cached-result? true
                          cas-hash :no-cas-file
                          :else :no-digest-file)
           :hash hash :cas-hash cas-hash :form form :var var :ns-effect? ns-effect?)
    (fs/create-dirs config/cache-dir)
    (cond-> (or (when-let [result-last-run (and (not no-cache?) (get-in results-last-run [hash :nextjournal/value]))]
                  (wrapped-with-metadata result-last-run visibility hash))
                (when cached-result?
                  (lookup-cached-result var hash cas-hash visibility))
                (eval+cache! form hash digest-file var no-cache? visibility))
      (seq opts-from-form-meta)
      (merge opts-from-form-meta))))

#_(eval-file "notebooks/test123.clj")
#_(eval-file "notebooks/how_clerk_works.clj")
#_(read+eval-cached {} {} #{:show} "(subs (slurp \"/usr/share/dict/words\") 0 1000)")

(defn clear-cache! []
  (swap! webserver/!doc dissoc :blob->result)
  (if (fs/exists? config/cache-dir)
    (do
      (fs/delete-tree config/cache-dir)
      (prn :cache-dir/deleted config/cache-dir))
    (prn :cache-dir/does-not-exist config/cache-dir)))

#_(blob->result @nextjournal.clerk.webserver/!doc)

(defn eval-analyzed-doc [{:as analyzed-doc :keys [->hash blocks visibility]}]
  (let [{:as evaluated-doc :keys [blob-ids]}
        (reduce (fn [{:as acc :keys [blob->result]} {:as cell :keys [type]}]
                  (let [{:as result :nextjournal/keys [blob-id]} (when (= :code type)
                                                                   (read+eval-cached blob->result ->hash visibility cell))]
                    (cond-> (update acc :blocks conj (cond-> cell result (assoc :result result)))
                      blob-id (update :blob-ids conj blob-id)
                      blob-id (assoc-in [:blob->result blob-id] result))))
                (assoc analyzed-doc :blocks [] :blob-ids #{}) blocks)]
    (-> evaluated-doc
        (update :blob->result select-keys blob-ids)
        (dissoc :blob-ids))))


(defn +eval-results [results-last-run parsed-doc]
  (let [analyzed-doc (hashing/build-graph parsed-doc)]
    (-> analyzed-doc
        (assoc :blob->result results-last-run :->hash (hashing/hash analyzed-doc) :ns *ns*)
        eval-analyzed-doc)))

(defn parse-file [file]
  (hashing/parse-file {:doc? true} file))

#_(parse-file "notebooks/elements.clj")
#_(parse-file "notebooks/visibility.clj")

#_(hashing/build-graph (parse-file "notebooks/test123.clj"))

(defn eval-doc
  ([doc] (eval-doc {} doc))
  ([results-last-run doc] (+eval-results results-last-run doc)))

(defn eval-file
  ([file] (eval-file {} file))
  ([results-last-run file] (eval-doc results-last-run (parse-file file))))

#_(eval-file "notebooks/hello.clj")
#_(eval-file "notebooks/rule_30.clj")
#_(eval-file "notebooks/visibility.clj")

(defn eval-string
  ([s] (eval-string {} s))
  ([results-last-run s]
   (eval-doc results-last-run (hashing/parse-clojure-string {:doc? true} s))))

#_(eval-string "(+ 39 3)")

(defmacro with-cache [form]
  `(let [result# (-> ~(pr-str form) eval-string :blob->result first val)]
     result#))

#_(with-cache (do (Thread/sleep 4200) 42))

(defmacro defcached [name expr]
  `(let [result# (-> ~(pr-str expr) eval-string :blob->result first val)]
     (def ~name result#)))

#_(defcached my-expansive-thing
    (do (Thread/sleep 4200) 42))

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
            {:keys [blob->result]} @webserver/!doc
            {:keys [result time-ms]} (time-ms (+eval-results blob->result doc))]
        ;; TODO diff to avoid flickering
        #_(webserver/update-doc! doc)
        (println (str "Clerk evaluated '" file "' in " time-ms "ms."))
        (webserver/update-doc! result))
      (catch Exception e
        (webserver/show-error! e)
        (throw e)))))

#_(show! @!last-file)

(defn recompute! []
  (binding [*ns* (:ns @webserver/!doc)]
    (let [{:keys [result time-ms]} (time-ms (eval-analyzed-doc @webserver/!doc))]
      (println (str "Clerk recomputed '" @!last-file "' in " time-ms "ms."))
      (webserver/update-doc! result))))

#_(recompute!)

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

;; these are refercing vars for convience when working at the REPL
(def md             v/md)
(def plotly         v/plotly)
(def vl             v/vl)
(def tex            v/tex)
(def notebook       v/notebook)
(def html           v/html)
(def code           v/code)
(def table          v/table)
(def use-headers    v/use-headers)
(def hide-result    v/hide-result)
(def doc-url        v/doc-url)
(def with-viewer    v/with-viewer)
(def with-viewers   v/with-viewers)
(def reset-viewers! v/reset-viewers!)
(def add-viewers    v/add-viewers)
(def add-viewers!   v/add-viewers!)
(def set-viewers!   v/set-viewers!)
(def ->value        v/->value)
(def update-value   v/update-value)
(def assoc-reduced  v/assoc-reduced)

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
         "readme"
         "rule_30"
         "slideshow"
         "visibility"
         "viewer_api"
         "viewer_api_meta"
         "viewer_d3_require"
         "viewers_nested"
         "viewer_normalization"
         "viewers/custom_markdown"
         "viewers/html"
         "viewers/image"
         "viewers/image_layouts"
         "viewers/in_text_eval"
         "viewers/instants"
         "viewers/markdown"
         "viewers/printing"
         "viewers/plotly"
         "viewers/table"
         "table_viewer_2"
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

(defn write-static-app!
  "Creates a static html app of the seq of `docs`. Customizable with an `opts` map with keys:

  - `:paths` a vector of relative paths to notebooks to include in the build
  - `:bundle?` builds a single page app versus a folder with an html page for each notebook (defaults to `true`)
  - `:out-path` a relative path to a folder to contain the static pages (defaults to `\"public/build\"`)
  - `:git/sha`, `:git/url` when both present, each page displays a link to `(str url \"blob\" sha path-to-notebook)`"
  [opts docs]
  (let [{:keys [out-path bundle? browse?]
         :or {out-path (str "public" fs/file-separator "build") bundle? true browse? true}} opts
        paths (mapv :file docs)
        path->doc (into {} (map (juxt :file :viewer)) docs)
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
            (let [out-html (str out-path fs/file-separator (str/replace path #"(.cljc?|.md)" ".html"))]
              (fs/create-dirs (fs/parent out-html))
              (spit out-html (view/->static-app (assoc static-app-opts :path->doc (hash-map path doc) :current-path path)))))))
    (when browse?
      (browse/browse-url (-> index-html fs/absolutize .toString path-to-url-canonicalize)))))

(defn stdout-reporter [{:as event :keys [stage state duration doc]}]
  (let [format-duration (partial format "%.3fms")
        duration (some-> duration format-duration)]
    (print (case stage
             :init (str "👷🏼 Clerk is building " (count state) " notebooks…\n🧐 Parsing… ")
             :parsed (str "Done in " duration ". ✅\n🔬 Analyzing… ")
             (:built :analyzed) (str "Done in " duration ". ✅\n")
             :building (str "🔨 Building \"" (:file doc) "\"… ")
             :finished (str "📦 Static app bundle created in " duration ". Total build time was " (-> event :total-duration format-duration) ".\n"))))
  (flush))

(defn expand-paths [paths]
  (->> (if (symbol? paths)
         (let [resolved (-> paths requiring-resolve deref)]
           (cond-> resolved
             (fn? resolved) (apply [])))
         paths)
       (mapcat (partial fs/glob "."))
       (filter (complement fs/directory?))
       (mapv (comp str fs/file))))

#_(expand-paths ["notebooks/di*.clj"])
#_(expand-paths `clerk-docs)
#_(do (defn my-paths [] ["notebooks/h*.clj"])
      (expand-paths `my-paths))
#_(expand-paths ["notebooks/viewers**"])

(defn build-static-app! [opts]
  (let [{:as opts :keys [paths]} (update opts :paths expand-paths)
        start (System/nanoTime)
        report-fn stdout-reporter
        state (mapv #(hash-map :file %) paths)
        _ (report-fn {:stage :init :state state})
        {state :result duration :time-ms} (time-ms (mapv (comp parse-file :file) state))
        _ (report-fn {:stage :parsed :state state :duration duration})
        {state :result duration :time-ms} (time-ms (mapv (comp (fn [doc] (assoc doc :->hash (hashing/hash doc) :ns *ns*))
                                                               hashing/build-graph) state))
        _ (report-fn {:stage :analyzed :state state :duration duration})
        state (mapv (fn [doc]
                      (report-fn {:stage :building :doc doc})
                      (let [{doc+viewer :result duration :time-ms} (time-ms
                                                                    (let [doc (eval-analyzed-doc doc)]
                                                                      (assoc doc :viewer (view/doc->viewer {:inline-results? true} doc))))]
                        (report-fn {:stage :built :doc doc+viewer :duration duration})
                        doc+viewer)) state)]
    {state :result duration :time-ms} (time-ms (write-static-app! opts state))
    (report-fn {:stage :finished :state state :duration duration :total-duration (elapsed-ms start)})))

#_(build-static-app! {:paths (take 5 clerk-docs)})
#_(build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/viewer_api.md"] :bundle? true})
#_(build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/viewer_api.md"] :bundle? false})
#_(build-static-app! {:paths ["notebooks/viewers/**"]})

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
