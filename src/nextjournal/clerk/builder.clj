(ns nextjournal.clerk.builder
  "Clerk's Static App Builder."
  (:require [babashka.fs :as fs]
            [babashka.process :refer [sh]]
            [clojure.edn :as edn]
            [clojure.java.browse :as browse]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.builder-ui :as builder-ui]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.webserver :as webserver]
            [nextjournal.clerk.config :as config])
  (:import (java.net URL)))

(def clerk-docs
  (into ["CHANGELOG.md"
         "notebooks/markdown.md"
         "notebooks/onwards.md"]
        (map #(str "notebooks/" % ".clj"))
        ["cards"
         "controlling_width"
         "docs"
         "document_linking"
         "hello"
         "how_clerk_works"
         "exec_status"
         "eval_cljs"
         "example"
         "fragments"
         "hiding_clerk_metadata"
         "js_import"
         "multiviewer"
         "pagination"
         "paren_soup"
         "readme"
         "rule_30"
         "slideshow"
         "visibility"
         "viewer_api"
         "viewer_api_meta"
         "viewer_classes"
         "viewer_d3_require"
         "viewers_nested"
         "viewer_normalization"
         "viewers/by_val_meta"
         "viewers/caption"
         "viewers/code"
         "viewers/control_lab"
         "viewers/custom_markdown"
         "viewers/grid"
         "viewers/html"
         "viewers/image"
         #_"viewers/image_layouts" ;; commented out until https://etc.usf.edu is back online
         "viewers/in_text_eval"
         "viewers/instants"
         "viewers/last_result"
         "viewers/markdown"
         "viewers/printing"
         "viewers/plotly"
         "viewers/table"
         "viewers/tex"
         "viewers/vega"
         "cherry"]))


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
      :ssr "🧱 Server Side Rendering… "
      :downloading-cache (str "⏬ Downloading distributed cache… ")
      :uploading-cache (str "⏫ Uploading distributed cache… ")
      :finished (str "📦 Static app bundle created in " duration ". Total build time was " (-> event :total-duration format-duration) ".\n"))))

(defn ^:private print+flush [x]
  (print x)
  (flush))

(defn stdout-reporter [build-event]
  (doto (describe-event build-event)
    print+flush))

(defn build-ui-reporter [{:as build-event :keys [stage]}]
  (when (= stage :init)
    (builder-ui/reset-build-state!)
    ((resolve 'nextjournal.clerk/show!) (find-ns 'nextjournal.clerk.builder-ui))
    (when-let [{:keys [port]} (and (get-in build-event [:build-opts :browse]) @webserver/!server)]
      (browse/browse-url (str "http://localhost:" port))))
  (stdout-reporter build-event)
  (builder-ui/add-build-event! build-event)
  (binding [*out* (java.io.StringWriter.)]
    ((resolve 'nextjournal.clerk/recompute!))))

(def default-out-path
  (str "public" fs/file-separator "build"))

(defn ^:private throw-when-empty [{:as build-opts :keys [paths paths-fn index]} expanded-paths]
  (if (empty? expanded-paths)
    (throw (ex-info "nothing to build" (merge {:expanded-paths expanded-paths} (select-keys build-opts [:paths :paths-fn :index]))))
    expanded-paths))

(defn ^:private maybe-add-index [{:as opts :keys [index]} resolved-paths]
  (when (contains? opts :index)
    (or (instance? URL index)
        (and (string? index) (fs/exists? index))
        (throw (ex-info "`:index` must be either an instance of java.net.URL or a string and point to an existing file" {:index index}))))
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
             paths-fn (let [ex-msg "`:path-fn` must be a qualified symbol pointing at an existing var."]
                        (when-not (qualified-symbol? paths-fn)
                          (throw (ex-info ex-msg {:paths-fn paths-fn})))
                        (if-let [resolved-var  (try (requiring-resolve paths-fn)
                                                    (catch Exception _e
                                                      (throw (ex-info ex-msg {:paths-fn paths-fn}))))]
                          (let [resolved-paths (try (cond-> @resolved-var
                                                      (fn? @resolved-var) (apply []))
                                                    (catch Exception e
                                                      (throw (ex-info (str "An error occured invoking `" (pr-str resolved-var) "`: " (ex-message e))
                                                                      {:paths-fn paths-fn} e))))]
                            (when-not (sequential? resolved-paths)
                              (throw (ex-info (str "`:paths-fn` must compute sequential value.") {:paths-fn paths-fn :resolved-paths resolved-paths})))
                            resolved-paths)
                          (throw (ex-info ex-msg {:paths-fn paths-fn})))))
       (mapcat (partial fs/glob "."))
       (filter (complement fs/directory?))
       (mapv (comp str fs/file))
       (maybe-add-index build-opts)
       (throw-when-empty build-opts)))

#_(expand-paths {:paths ["notebooks/di*.clj"]})
#_(expand-paths {:paths ['notebooks/rule_30.clj]})
#_(expand-paths {:paths-fn `clerk-docs})
#_(expand-paths {:paths-fn `clerk-docs-2})
#_(do (defn my-paths [] ["notebooks/h*.clj"])
      (expand-paths {:paths-fn `my-paths}))
#_(expand-paths {:paths ["notebooks/viewers**"]})

(defn process-build-opts [{:as opts :keys [paths index expand-paths?]}]
  (merge {:out-path default-out-path
          :bundle? false
          :browse? false
          :report-fn (if @webserver/!server build-ui-reporter stdout-reporter)}
         (let [opts+index (cond-> opts
                            index (assoc :index (str index)))
               {:as opts' :keys [expanded-paths]} (cond-> opts+index
                                                    expand-paths? (merge (try {:expanded-paths (expand-paths opts+index)}
                                                                              (catch Exception e
                                                                                {:error e}))))]
           (-> opts'
               (update :resource->url #(merge {} %2 %1) @config/!resource->url)
               (cond-> #_opts'
                 expand-paths?
                 (dissoc :expand-paths?)
                 (and (not index) (= 1 (count expanded-paths)))
                 (assoc :index (first expanded-paths))
                 (and (not index) (< 1 (count expanded-paths)) (every? (complement #{"index.clj"}) expanded-paths))
                 (as-> opts
                   (let [index (io/resource "nextjournal/clerk/index.clj")]
                     (-> opts (assoc :index index) (update :expanded-paths conj index)))))))))

#_(process-build-opts {:index 'book.clj :expand-paths? true})
#_(process-build-opts {:paths ["notebooks/rule_30.clj"] :expand-paths? true})
#_(process-build-opts {:paths ["notebooks/rule_30.clj"
                               "notebooks/markdown.md"] :expand-paths? true})

(defn build-path->url [{:as opts :keys [bundle?]} docs]
  (into {}
        (map (comp (juxt identity #(cond-> (->> % (viewer/map-index opts) strip-index) (not bundle?) ->html-extension))
                   str :file))
        docs))
#_(build-path->url {:bundle? false} [{:file "notebooks/foo.clj"} {:file "index.clj"}])
#_(build-path->url {:bundle? true}  [{:file "notebooks/foo.clj"} {:file "index.clj"}])

(defn build-static-app-opts [{:as opts :keys [bundle? out-path browse? index]} docs]
  (let [path->doc (into {} (map (juxt (comp str :file) :viewer)) docs)]
    (assoc opts
           :bundle? bundle?
           :path->doc path->doc
           :paths (vec (keys path->doc))
           :path->url (build-path->url opts docs))))

(defn ssr!
  "Shells out to node to generate server-side-rendered html."
  [{:as static-app-opts :keys [report-fn resource->url]}]
  (report-fn {:stage :ssr})
  (let [{duration :time-ms :keys [result]}
        (eval/time-ms (sh {:in (str "import '" (resource->url "/js/viewer.js") "';"
                                    "console.log(nextjournal.clerk.sci_env.ssr(" (pr-str (pr-str static-app-opts)) "))")}
                          "node"
                          "--abort-on-uncaught-exception"
                          "--experimental-network-imports"
                          "--input-type=module"
                          "--trace-warnings"))
        {:keys [out err exit]} result]
    (if (= 0 exit)
      (do
        (report-fn {:stage :done :duration duration})
        (assoc static-app-opts :html out))
      (throw (ex-info (str "Clerk ssr! failed\n" out "\n" err) result)))))

(defn cleanup [build-opts]
  (select-keys build-opts
               [:bundle? :path->doc :path->url :current-path :resource->url :exclude-js? :index :html]))

(defn write-static-app!
  [opts docs]
  (let [{:as opts :keys [bundle? out-path browse? ssr?]} (process-build-opts opts)
        index-html (str out-path fs/file-separator "index.html")
        {:as static-app-opts :keys [path->url path->doc]} (build-static-app-opts (viewer/update-if opts :index str) docs)]
    (when-not (contains? (-> path->url vals set) "")
      (throw (ex-info "Index must have been processed at this point" {:opts opts :docs docs})))
    (when-not (fs/exists? (fs/parent index-html))
      (fs/create-dirs (fs/parent index-html)))
    (if bundle?
      (spit index-html (view/->html (cleanup static-app-opts)))
      (doseq [[path doc] path->doc]
        (let [out-html (str out-path fs/file-separator (->> path (viewer/map-index opts) ->html-extension))]
          (fs/create-dirs (fs/parent out-html))
          (spit out-html (view/->html (-> static-app-opts
                                          (assoc :path->doc (hash-map path doc) :current-path path)
                                          (cond-> ssr? ssr!)
                                          cleanup))))))
    (when browse?
      (browse/browse-url (-> index-html fs/absolutize .toString path-to-url-canonicalize)))
    {:docs docs
     :index-html index-html
     :build-href (if (and @webserver/!server (= out-path default-out-path)) "/build/" index-html)}))


(defn compile-css!
  "Compiles a minimal tailwind css stylesheet with only the used styles included, replaces the generated stylesheet link in html pages."
  [{:as opts :keys [resource->url]} docs]
  (let [tw-folder (fs/create-dirs "tw")
        tw-input (str tw-folder "/input.css")
        tw-config (str tw-folder "/tailwind.config.cjs")
        tw-output (str tw-folder "/viewer.css")
        tw-viewer (str tw-folder "/viewer.js")]
    (spit tw-config (slurp (io/resource "stylesheets/tailwind.config.js")))
    ;; NOTE: a .cjs extension is safer in case the current npm project is of type module (like Clerk's): in this case all .js files
    ;; are treated as ES modules and this is not the case of our tw config.
    (spit tw-input (slurp (io/resource "stylesheets/viewer.css")))
    (spit tw-viewer (slurp (get resource->url "/js/viewer.js")))
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
      (println err)
      (println out)
      (when-not (= 0 exit)
        (throw (ex-info (str "Clerk build! failed\n" out "\n" err) ret))))
    (let [url (viewer/store+get-cas-url! (assoc opts :ext "css") (fs/read-all-bytes tw-output))]
      (fs/delete-tree tw-folder)
      (update opts :resource->url assoc "/css/viewer.css" url))))

(defn doc-url
  ([opts doc file path] (doc-url opts doc file path nil))
  ([{:as opts :keys [bundle?]} docs file path fragment]
   (let [url (get (build-path->url (viewer/update-if opts :index str) docs) path)]
     (if bundle?
       (str "#/" url)
       (str (viewer/relative-root-prefix-from (viewer/map-index opts file))
            url (when fragment (str "#" fragment)))))))

(defn read-opts-from-deps-edn! []
  (if (fs/exists? "deps.edn")
    (let [deps-edn (edn/read-string (slurp "deps.edn"))]
      (if-some [clerk-alias (get-in deps-edn [:aliases :nextjournal/clerk])]
        (get clerk-alias :exec-args
             {:error "No `:exec-args` found in `:nextjournal/clerk` alias."})
        {:error "No `:nextjournal/clerk` alias found in `deps.edn`."}))
    {:error "No `deps.edn` found in project."}))

(def ^:dynamic ^:private *build-opts* nil)
(defn index-paths []
  (let [{:as opts :keys [index error]} (or *build-opts* (read-opts-from-deps-edn!))]
    (if error opts {:paths (remove #{index "index.clj"} (expand-paths opts))})))

#_(index-paths)
#_(nextjournal.clerk/show! 'nextjournal.clerk.index)

(defn build-static-app! [{:as opts :keys [bundle?]}]
  (let [{:as opts :keys [download-cache-fn upload-cache-fn report-fn compile-css? expanded-paths error]}
        (try (process-build-opts (assoc opts :expand-paths? true))
             (catch Exception e
               {:error e}))
        start (System/nanoTime)
        state (mapv #(hash-map :file %) expanded-paths)
        _ (report-fn {:stage :init :state state :build-opts opts})
        _ (when error
            (report-fn {:stage :parsed :error error :build-opts opts})
            (throw error))
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
        state (mapv (fn [{:as doc :keys [file]} idx]
                      (report-fn {:stage :building :doc doc :idx idx})
                      (let [{result :result duration :time-ms} (eval/time-ms
                                                                (try
                                                                  (binding [*build-opts* opts
                                                                            viewer/doc-url (partial doc-url opts state file)]
                                                                    (let [doc (eval/eval-analyzed-doc doc)]
                                                                      (assoc doc :viewer (view/doc->viewer (assoc opts :static-build? true
                                                                                                                  :nav-path (str file)) doc))))
                                                                  (catch Exception e
                                                                    {:error e})))]
                        (report-fn (merge {:stage :built :duration duration :idx idx}
                                          (if (:error result) result {:doc result})))
                        result)) state (range))
        _ (when-let [first-error (some :error state)]
            (throw first-error))
        opts (if compile-css?
               (do
                 (report-fn {:stage :compiling-css})
                 (let [{duration :time-ms opts :result} (eval/time-ms (compile-css! opts state))]
                   (report-fn {:stage :done :duration duration})
                   opts))
               opts)
        {state :result duration :time-ms} (eval/time-ms (write-static-app! opts state))]
    (when upload-cache-fn
      (report-fn {:stage :uploading-cache})
      (let [{duration :time-ms} (eval/time-ms (upload-cache-fn state))]
        (report-fn {:stage :done :duration duration})))
    (report-fn {:stage :finished :state state :duration duration :total-duration (eval/elapsed-ms start)})))

(comment
  (build-static-app! {:paths clerk-docs :bundle? true})
  (build-static-app! {:paths ["notebooks/index.clj" "notebooks/rule_30.clj" "notebooks/viewer_api.md"] :index "notebooks/index.clj"})
  (build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/markdown.md"] :bundle? false :browse? false})
  (build-static-app! {:paths ["notebooks/viewers/**"]})
  (build-static-app! {:index "notebooks/rule_30.clj" :git/sha "bd85a3de12d34a0622eb5b94d82c9e73b95412d1" :git/url "https://github.com/nextjournal/clerk"})
  (reset! config/!resource->url @config/!asset-map)
  (swap! config/!resource->url dissoc "/css/viewer.css")

  (build-static-app! {:ssr? true
                      :exclude-js? true
                      ;; test against cljs release `bb build:js`
                      :resource->url {"/js/viewer.js" "./build/viewer.js"}
                      :index "notebooks/rule_30.clj"})

  (build-static-app! {:ssr? true
                      :compile-css? true
                      ;; test against cljs release `bb build:js`
                      :resource->url {"/js/viewer.js" "./build/viewer.js"}
                      :index "notebooks/rule_30.clj"})
  (fs/delete-tree "public/build")
  (build-static-app! {:compile-css? true
                      :index "notebooks/rule_30.clj"
                      :paths ["notebooks/hello.clj"
                              "notebooks/markdown.md"]})
  (build-static-app! {;; test against cljs release `bb build:js`
                      :resource->url {"/js/viewer.js" "/viewer.js"}
                      :paths ["notebooks/cherry.clj"]
                      :out-path "build"})
  (build-static-app! {:paths ["CHANGELOG.md"
                              "notebooks/markdown.md"
                              "notebooks/viewers/html.clj"]
                      :bundle? true
                      :git/sha "d60f5417"
                      :git/url "https://github.com/nextjournal/clerk"}))
