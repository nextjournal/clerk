(ns nextjournal.clerk.builder
  "Clerk's Static App Builder."
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.set :as set]
            [clojure.string :as str]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.builder-ui :as builder-ui]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.webserver :as webserver]))

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

(defn describe-event [{:as event :keys [stage state duration doc]}]
  (let [format-duration (partial format "%.3fms")
        duration (some-> duration format-duration)]
    (case stage
      :init (str "👷🏼 Clerk is building " (count state) " notebooks…\n🧐 Parsing… ")
      :parsed (str "Done in " duration ". ✅\n🔬 Analyzing… ")
      (:built :analyzed :done) (str "Done in " duration ". ✅\n")
      :building (str "🔨 Building \"" (:file doc) "\"… ")
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

(defn ^:private migrate-deprecated-opts [opts]
  (set/rename-keys opts {:bundle? :bundle
                         :browse? :browse}))

(defn process-build-opts [{:as opts :keys [paths index]}]
  (merge {:out-path default-out-path
          :bundle false
          :browse false
          :report-fn (if @webserver/!server build-ui-reporter stdout-reporter)}
         (cond-> (migrate-deprecated-opts opts)
           index (assoc :index (str index)))))

#_(process-build-opts {:index 'book.clj})

(defn write-static-app!
  [opts docs]
  (let [{:as opts :keys [bundle out-path browse index]} (process-build-opts opts)
        paths (mapv :file docs)
        path->doc (into {} (map (juxt :file :viewer)) docs)
        path->url (into {} (map (juxt identity #(cond-> (strip-index %) (not bundle) ->html-extension))) paths)
        static-app-opts (assoc opts :bundle bundle :path->doc path->doc :paths (vec (keys path->doc)) :path->url path->url)
        index-html (str out-path fs/file-separator "index.html")]
    (when-not (fs/exists? (fs/parent index-html))
      (fs/create-dirs (fs/parent index-html)))
    (if bundle
      (spit index-html (view/->static-app static-app-opts))
      (do (when-not (contains? (-> path->url vals set) (->html-extension (str index))) ;; no user-defined index page
            (spit index-html (view/->static-app (dissoc static-app-opts :path->doc))))
          (doseq [[path doc] path->doc]
            (let [path-with-index-mapped (if index ({index "index.clj"} path path) path)
                  out-html (str out-path fs/file-separator (->html-extension path-with-index-mapped))]
              (fs/create-dirs (fs/parent out-html))
              (spit out-html (view/->static-app (assoc static-app-opts :path->doc (hash-map path doc) :current-path path)))))))
    (when browse
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
       (mapv (comp str fs/file))))

#_(expand-paths {:paths ["notebooks/di*.clj"]})
#_(expand-paths {:paths ['notebooks/rule_30.clj]})
#_(expand-paths {:paths-fn `clerk-docs})
#_(expand-paths {:paths-fn `clerk-docs-2})
#_(do (defn my-paths [] ["notebooks/h*.clj"])
      (expand-paths {:paths-fn `my-paths}))
#_(expand-paths {:paths ["notebooks/viewers**"]})

(defn build-static-app! [opts]
  (let [{:as opts :keys [expanded-paths paths download-cache-fn upload-cache-fn bundle report-fn]} (assoc (process-build-opts opts) :expanded-paths (expand-paths opts))
        _ (when (empty? expanded-paths)
            (throw (ex-info "nothing to build" {:expanded-paths expanded-paths :paths paths})))
        start (System/nanoTime)
        state (mapv #(hash-map :file %) expanded-paths)
        _ (report-fn {:stage :init :state state :build-opts opts})
        {state :result duration :time-ms} (eval/time-ms (mapv (comp (partial parser/parse-file {:doc? true}) :file) state))
        _ (report-fn {:stage :parsed :state state :duration duration})
        {state :result duration :time-ms} (eval/time-ms (mapv (comp analyzer/hash
                                                                    analyzer/build-graph) state))
        _ (report-fn {:stage :analyzed :state state :duration duration})
        _ (when download-cache-fn
            (report-fn {:stage :downloading-cache})
            (let [{duration :time-ms} (eval/time-ms (download-cache-fn state))]
              (report-fn {:stage :done :duration duration})))
        docs (mapv (fn [doc idx]
                     (report-fn {:stage :building :doc doc :idx idx})
                     (let [{doc+viewer :result duration :time-ms} (eval/time-ms
                                                                   (let [doc (eval/eval-analyzed-doc doc)]
                                                                     (assoc doc :viewer (view/doc->viewer (assoc opts :inline-results? true) doc))))]
                       (report-fn {:stage :built :doc doc+viewer :duration duration :idx idx})
                       doc+viewer)) state (range))
        {state :result duration :time-ms} (eval/time-ms (write-static-app! opts docs))]
    (when upload-cache-fn
      (report-fn {:stage :uploading-cache})
      (let [{duration :time-ms} (eval/time-ms (upload-cache-fn state))]
        (report-fn {:stage :done :duration duration})))
    (report-fn {:stage :finished :state state :duration duration :total-duration (eval/elapsed-ms start)})))

#_(build-static-app! {:paths (take 15 clerk-docs)})
#_(build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/viewer_api.md"] :bundle true})
#_(build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/markdown.md"] :bundle false :browse false})
#_(build-static-app! {:paths ["notebooks/viewers/**"]})
#_(build-static-app! {:index "notebooks/rule_30.clj"  :git/sha "bd85a3de12d34a0622eb5b94d82c9e73b95412d1" :git/url "https://github.com/nextjournal/clerk"})
