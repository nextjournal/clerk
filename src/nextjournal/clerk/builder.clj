(ns nextjournal.clerk.builder
  "Clerk's Static App Builder."
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.java.shell :refer [sh]]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.builder-ui :as builder-ui]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.webserver :as webserver]
            [nextjournal.clerk.config :as config]))

(def clerk-docs
  (into ["CHANGELOG.md"
         "notebooks/markdown.md"
         "notebooks/onwards.md"]
        (map #(str "notebooks/" % ".clj"))
        ["cards"
         "docs"
         "hello"
         "how_clerk_works"
         "eval_cljs"
         "example"
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
         "viewers/grid"
         "viewers/html"
         "viewers/image"
         "viewers/image_layouts"
         "viewers/in_text_eval"
         "viewers/instants"
         "viewers/last_result"
         "viewers/markdown"
         "viewers/printing"
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
  (str/replace path #"\.(cljc?|md)$" ".html"))

#_(->html-extension "hello.clj")

(defn- path-to-url-canonicalize
  "Canonicalizes the system specific path separators in `PATH` (e.g. `\\`
  on MS-Windows) to URL-compatible forward slashes."
  [path]
  (str/replace path fs/file-separator "/"))

(defn describe-event [{:as event :keys [stage state duration doc error]}]
  (let [format-duration (partial format "%.3fms")
        duration (some-> duration format-duration)]
    (case stage
      :init (str "👷🏼 Clerk is building " (count state) " notebooks…\n🧐 Parsing… ")
      :parsed (str "Done in " duration ". ✅\n🔬 Analyzing… ")
      (:built :analyzed :done) (if error
                                 (str "Errored in " duration ". ❌\n")
                                 (str "Done in " duration ". ✅\n"))
      :building (str "🔨 Building \"" (:file doc) "\"… ")
      :compiling-css "🎨 Compiling CSS… "
      :downloading-cache (str "⏬ Downloading distributed cache… ")
      :uploading-cache (str "⏫ Uploading distributed cache… ")
      :finished (str "📦 Static app bundle created in " duration ". Total build time was " (-> event :total-duration format-duration) ".\n"))))

(defn stdout-reporter [build-event]
  (doto (describe-event build-event)
    (print)
    (do (flush))))

(defn build-ui-reporter [{:as build-event :keys [stage]}]
  (when (= stage :init)
    (builder-ui/reset-build-state!)
    ((resolve 'nextjournal.clerk/show!) (clojure.java.io/resource "nextjournal/clerk/builder_ui.clj"))
    (when-let [{:keys [port]} (and (get-in build-event [:build-opts :browse]) @webserver/!server)]
      (browse/browse-url (str "http://localhost:" port))))
  (stdout-reporter build-event)
  (builder-ui/add-build-event! build-event)
  (binding [*out* (java.io.StringWriter.)]
    ((resolve 'nextjournal.clerk/recompute!))))

(def default-out-path
  (str "public" fs/file-separator "build"))

(defn process-build-opts [{:as opts :keys [paths index]}]
  (merge {:out-path default-out-path
          :bundle? false
          :browse? false
          :report-fn (if @webserver/!server build-ui-reporter stdout-reporter)}
         (cond-> opts
           index (assoc :index (str index)))))

#_(process-build-opts {:index 'book.clj})

(defn build-static-app-opts [{:as opts :keys [bundle? out-path browse? index]} docs]
  (let [paths (mapv :file docs)
        path->doc (into {} (map (juxt :file :viewer)) docs)
        path->url (into {} (map (juxt identity #(cond-> (->> % (view/map-index opts) strip-index) (not bundle?) ->html-extension))) paths)]
    (assoc opts :bundle? bundle? :path->doc path->doc :paths (vec (keys path->doc)) :path->url path->url)))

(defn ssr!
  "Shells out to node to generate server-side-rendered html."
  [static-app-opts]
  (let [{:as ret :keys [out err exit]}
        (sh "node"
            "--abort-on-uncaught-exception"
            "--experimental-network-imports"
            "--input-type=module"
            "--eval"
            (str "import '" (@config/!asset-map "/js/viewer.js") "';"
                 "console.log(nextjournal.clerk.static_app.ssr(" (pr-str (pr-str static-app-opts)) "))"))]
    (if (= 0 exit)
      (assoc static-app-opts :html out)
      (throw (ex-info (str "Clerk ssr! failed\n" out "\n" err) ret)))))

(defn write-static-app!
  [opts docs]
  (let [{:as opts :keys [bundle? out-path browse? index ssr?]} (process-build-opts opts)
        index-html (str out-path fs/file-separator "index.html")
        {:as static-app-opts :keys [path->url path->doc]} (build-static-app-opts opts docs)]
    (when-not (fs/exists? (fs/parent index-html))
      (fs/create-dirs (fs/parent index-html)))
    (if bundle?
      (spit index-html (view/->static-app static-app-opts))
      (do (when-not (contains? (-> path->url vals set) "") ;; no user-defined index page
            (spit index-html (view/->static-app (dissoc static-app-opts :path->doc))))
          (doseq [[path doc] path->doc]
            (let [out-html (str out-path fs/file-separator (->> path (view/map-index opts) ->html-extension))]
              (fs/create-dirs (fs/parent out-html))
              (spit out-html (view/->static-app (cond-> (assoc static-app-opts :path->doc (hash-map path doc) :current-path path)
                                                  ssr? ssr!)))))))
    (when browse?
      (browse/browse-url (-> index-html fs/absolutize .toString path-to-url-canonicalize)))
    {:docs docs
     :index-html index-html
     :build-href (if (and @webserver/!server (= out-path default-out-path)) "/build" index-html)}))

(defn ^:private maybe-add-index [{:as build-opts :keys [paths paths-fn index]} resolved-paths]
  (when (and index (or (not (string? index)) (not (fs/exists? index))))
    (throw (ex-info "`:index` must be string and point to existing file" {:index index})))
  (cond-> resolved-paths
    (and index (not (contains? (set resolved-paths) index)))
    (conj index)))

#_(maybe-add-index {:index "book.clj"} nil)

(defn ^:private throw-when-empty [{:as build-opts :keys [paths paths-fn index]} expanded-paths]
  (if (empty? expanded-paths)
    (throw (ex-info "nothing to build" (merge {:expanded-paths expanded-paths} (select-keys build-opts [:paths :paths-fn :index]))))
    expanded-paths))

(defn expand-paths [{:as build-opts :keys [paths paths-fn index]}]
  (when (and paths paths-fn)
    (binding [*out* *err*]
      (println "[info] both `:paths` and `:paths-fn` are set, `:paths` will take precendence.")))
  (when (not (or paths paths-fn index))
    (throw (ex-info "must set either `:paths`, `:paths-fn` or `:index`." {:build-opts build-opts})))
  (->> (cond paths (if (sequential? paths)
                     paths
                     (throw (ex-info "`:paths` must be sequential" {:paths paths})))
             paths-fn (if (qualified-symbol? paths-fn)
                        (try
                          (if-let [resolved-var  (requiring-resolve paths-fn)]
                            (let [resolved-paths (cond-> @resolved-var
                                                   (fn? @resolved-var) (apply []))]
                              (when-not (sequential? resolved-paths)
                                (throw (ex-info (str "#'" paths-fn " must be sequential.") {:paths-fn paths-fn :resolved-paths resolved-paths})))
                              resolved-paths)
                            (throw (ex-info (str "#'" paths-fn " cannot be resolved.") {:paths-fn paths-fn}))))
                        (throw (ex-info "`:path-fn` must be a qualified symbol pointing at an existing var." {:paths-fn paths-fn}))))
       (maybe-add-index build-opts)
       (mapcat (partial fs/glob "."))
       (filter (complement fs/directory?))
       (mapv (comp str fs/file))
       (throw-when-empty build-opts)))

#_(expand-paths {:paths ["notebooks/di*.clj"]})
#_(expand-paths {:paths ['notebooks/rule_30.clj]})
#_(expand-paths {:paths-fn `clerk-docs})
#_(expand-paths {:paths-fn `clerk-docs-2})
#_(do (defn my-paths [] ["notebooks/h*.clj"])
      (expand-paths {:paths-fn `my-paths}))
#_(expand-paths {:paths ["notebooks/viewers**"]})

(defn compile-css!
  "Compiles a minimal tailwind css stylesheet with only the used styles included, replaces the generated stylesheet link in html pages."
  [{:keys [out-path]} docs]
  (let [tw-folder (fs/create-dirs "tw")
        tw-input (str tw-folder "/input.css")
        tw-config (str tw-folder "/tailwind.config.cjs")
        tw-output (str tw-folder "/viewer.css")
        tw-viewer (str tw-folder "/viewer.js")]
    (spit tw-config (slurp (io/resource "stylesheets/tailwind.config.js")))
    ;; NOTE: a .cjs extension is safer in case the current npm project is of type module (like Clerk's): in this case all .js files
    ;; are treated as ES modules and this is not the case of our tw config.
    (spit tw-input (slurp (io/resource "stylesheets/viewer.css")))
    (spit tw-viewer (slurp (get @config/!asset-map "/js/viewer.js")))
    (doseq [{:keys [file viewer]} docs]
      (spit (let [path (fs/path tw-folder (str/replace file #"\.(cljc?|md)$" ".edn"))]
              (fs/create-dirs (fs/parent path))
              (str path))
            (pr-str viewer)))
    (let [{:as ret :keys [out err exit]}
          (sh "tailwindcss"
              "--input"  tw-input
              "--config" tw-config
              ;; FIXME: pass inline
              ;;"--content" (str tw-viewer)
              ;;"--content" (str tw-folder "/**/*.edn")
              "--output" tw-output
              "--minify")]
      (when-not (= 0 exit)
        (throw (ex-info (str "Clerk build! failed\n" out "\n" err) ret))))
    (let [content-addressed (fs/file "_data" (str (analyzer/valuehash (slurp tw-output)) ".css"))]
      (fs/create-dirs (fs/parent (fs/file out-path content-addressed)))
      (when-not (fs/exists? (fs/file out-path content-addressed))
        (fs/copy tw-output (fs/file out-path content-addressed)))
      (swap! config/!resource->url assoc "/css/viewer.css" (str content-addressed))
      ;; cleanup
      (fs/delete-tree tw-folder))))

#_(fs/delete-tree "public/build")
#_(build-static-app! {:paths ["notebooks/hello.clj" "notebooks/markdown.md" "notebooks/viewers/image.clj"]
                      :bundle? false
                      :compile-css? true})

(defn build-static-app! [opts]
  (let [{:as opts :keys [download-cache-fn upload-cache-fn report-fn compile-css?]}
        (process-build-opts opts)
        {:keys [expanded-paths error]} (try {:expanded-paths (expand-paths opts)}
                                            (catch Exception e
                                              {:error e}))
        start (System/nanoTime)
        state (mapv #(hash-map :file %) expanded-paths)
        _ (report-fn {:stage :init :state state :build-opts opts})
        _ (when error
            (do (report-fn {:stage :parsed :error error :build-opts opts})
                (throw error)))
        {state :result duration :time-ms} (eval/time-ms (mapv (comp (partial parser/parse-file {:doc? true}) :file) state))
        _ (report-fn {:stage :parsed :state state :duration duration})
        {state :result duration :time-ms} (eval/time-ms (reduce (fn [state doc]
                                                                  (try (conj state (-> doc analyzer/build-graph analyzer/hash))
                                                                       (catch Exception e
                                                                         (reduced {:error e}))))
                                                                []
                                                                state))
        _ (if-let [error (:error state)]
            (do (report-fn {:stage :analyzed :error error :duration duration})
                (throw error))
            (report-fn {:stage :analyzed :state state :duration duration}))
        _ (when download-cache-fn
            (report-fn {:stage :downloading-cache})
            (let [{duration :time-ms} (eval/time-ms (download-cache-fn state))]
              (report-fn {:stage :done :duration duration})))
        state (mapv (fn [doc idx]
                      (report-fn {:stage :building :doc doc :idx idx})
                      (let [{result :result duration :time-ms} (eval/time-ms
                                                                (try
                                                                  (let [doc (eval/eval-analyzed-doc doc)]
                                                                    (assoc doc :viewer (view/doc->viewer (assoc opts :inline-results? true) doc)))
                                                                  (catch Exception e
                                                                    {:error e})))]
                        (report-fn (merge {:stage :built :duration duration :idx idx}
                                          (if (:error result) result {:doc result})))
                        result)) state (range))
        _ (when-let [first-error (some :error state)]
            (throw first-error))
        _ (when compile-css?
            (report-fn {:stage :compiling-css})
            (let [{duration :time-ms} (eval/time-ms (compile-css! opts state))]
              (report-fn {:stage :done :duration duration})))
        {state :result duration :time-ms} (eval/time-ms (write-static-app! opts state))]
    (when upload-cache-fn
      (report-fn {:stage :uploading-cache})
      (let [{duration :time-ms} (eval/time-ms (upload-cache-fn state))]
        (report-fn {:stage :done :duration duration})))
    (report-fn {:stage :finished :state state :duration duration :total-duration (eval/elapsed-ms start)})))


#_(build-static-app! {:ssr? true :index "notebooks/rule_30.clj" :browse? true})
#_(build-static-app! {:paths clerk-docs :bundle? true})
#_(build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/viewer_api.md"] :bundle? true})
#_(build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/markdown.md"] :bundle? false :browse? false})
#_(build-static-app! {:paths ["notebooks/viewers/**"]})
#_(build-static-app! {:index "notebooks/rule_30.clj" :git/sha "bd85a3de12d34a0622eb5b94d82c9e73b95412d1" :git/url "https://github.com/nextjournal/clerk"})
#_(swap! config/!resource->url assoc
         "/js/viewer.js" (-> config/lookup-url slurp clojure.edn/read-string (get "/js/viewer.js")))
#_(swap! config/!resource->url dissoc "/css/viewer.css")
#_(fs/delete-tree "public/build")
#_(build-static-app! {:compile-css? true
                      :index "notebooks/rule_30.clj"
                      :paths ["notebooks/hello.clj"
                              "notebooks/markdown.md"]})
