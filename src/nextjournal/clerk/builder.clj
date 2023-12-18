(ns nextjournal.clerk.builder
  "Clerk's Static App Builder."
  (:require [babashka.fs :as fs]
            [babashka.process :refer [sh]]
            [clojure.java.browse :as browse]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.builder-ui :as builder-ui]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.paths :as paths]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.webserver :as webserver]))

(def clerk-docs
  (into ["CHANGELOG.md"
         "README.md"
         "notebooks/markdown.md"
         "notebooks/markdown_fences.md"
         "notebooks/onwards.md"]
        (map #(str "notebooks/" % ".clj"))
        ["cards"
         "cherry"
         "controlling_width"
         "docs"
         "document_linking"
         "editor"
         "hello"
         "how_clerk_works"
         "exec_status"
         "eval_cljs"
         "example"
         "fragments"
         "hiding_clerk_metadata"
         "js_import"
         "meta_toc"
         "multiviewer"
         "pagination"
         "paren_soup"
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

(def builtin-index
  (io/resource "nextjournal/clerk/index.clj"))

(defn process-build-opts [{:as opts :keys [package index expand-paths?]}]
  (merge {:out-path default-out-path
          :package :directory
          :render-router :fetch-edn
          :browse? false
          :report-fn (if @webserver/!server build-ui-reporter stdout-reporter)}
         (let [opts+index (cond-> opts
                            index (assoc :index (str index)))
               {:as opts' :keys [expanded-paths]} (cond-> opts+index
                                                    expand-paths? (merge (paths/expand-paths opts+index)))]
           (-> opts'
               (update :resource->url #(merge {} %2 %1) @config/!resource->url)
               (cond-> #_opts'
                 (= :single-file package)
                 (assoc :render-router :bundle)
                 expand-paths?
                 (dissoc :expand-paths?)
                 (and (not index) (< 1 (count expanded-paths)) (every? (complement viewer/index-path?) expanded-paths))
                 (as-> opts
                     (-> opts (assoc :index builtin-index) (update :expanded-paths conj builtin-index))))))))

#_(process-build-opts {:index 'book.clj :expand-paths? true})
#_(process-build-opts {:paths ["notebooks/rule_30.clj"] :expand-paths? true})
#_(process-build-opts {:paths ["notebooks/rule_30.clj"
                               "notebooks/markdown.md"] :expand-paths? true})

(defn build-static-app-opts [opts docs]
  (let [path->doc (into {} (map (juxt (comp str fs/strip-ext strip-index (partial viewer/map-index opts) :file) :viewer)) docs)]
    (assoc opts
           :path->doc path->doc
           :paths (vec (keys path->doc)))))

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
               [:package :render-router :path->doc :current-path :resource->url :exclude-js? :index :html]))

(defn write-static-app!
  [opts docs]
  (let [{:keys [package out-path browse? ssr?]} opts
        index-html (str out-path fs/file-separator "index.html")
        {:as static-app-opts :keys [path->doc]} (build-static-app-opts opts docs)]
    (when-not (contains? (set (keys path->doc)) "")
      (throw (ex-info "Index must have been processed at this point" {:static-app-opts static-app-opts})))
    (when-not (fs/exists? (fs/parent index-html))
      (fs/create-dirs (fs/parent index-html)))
    (if (= :single-file package)
      (spit index-html (view/->html (cleanup static-app-opts)))
      (doseq [[path doc] path->doc]
        (let [out-html (fs/file out-path path "index.html")]
          (fs/create-dirs (fs/parent out-html))
          (spit (fs/file out-path (str (or (not-empty path) "index") ".edn"))
                (viewer/->edn doc))
          (spit out-html (view/->html (-> static-app-opts
                                          (dissoc :path->doc)
                                          (assoc :current-path path)
                                          (cond-> ssr? ssr!)
                                          cleanup))))))
    (when browse?
      (browse/browse-url (if-let [{:keys [port]} (and (= out-path "public/build") @webserver/!server)]
                           (str "http://localhost:" port "/build/")
                           (-> index-html fs/absolutize .toString path-to-url-canonicalize))))
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
          (try (sh "tailwindcss"
                   "--input"  tw-input
                   "--config" tw-config
                   ;; FIXME: pass inline
                   ;;"--content" (str tw-viewer)
                   ;;"--content" (str tw-folder "/**/*.edn")
                   "--output" tw-output
                   "--minify")
               (catch java.io.IOException _
                 (throw (Exception. "Clerk could not find the `tailwindcss` executable. Please install it using `npm install -D tailwindcss` and try again."))))]
      (println err)
      (println out)
      (when-not (= 0 exit)
        (throw (ex-info (str "Clerk build! failed\n" out "\n" err) ret))))
    (let [url (viewer/store+get-cas-url! (assoc opts :ext "css") (fs/read-all-bytes tw-output))]
      (fs/delete-tree tw-folder)
      (update opts :resource->url assoc "/css/viewer.css" url))))

(defn doc-url
  ([opts file path] (doc-url opts file path nil))
  ([{:as opts :keys [package]} file path fragment]
   (if (= :single-file package)
     (cond-> (str "#/" path)
       fragment (str ":" fragment))
     (str (viewer/relative-root-prefix-from (viewer/map-index opts file)) path
          (when fragment (str "#" fragment))))))

(defn build-static-app! [opts]
  (let [{:as opts :keys [download-cache-fn upload-cache-fn report-fn compile-css? expanded-paths error]}
        (process-build-opts (assoc opts :expand-paths? true))
        start (System/nanoTime)
        state (mapv #(hash-map :file %) expanded-paths)
        _ (report-fn {:stage :init :state state :build-opts opts})
        _ (when error
            (report-fn {:stage :parsed :error error :build-opts opts})
            (throw (if-not (string? error) error (ex-info error (dissoc opts :report-fn)))))
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
                                                                  (binding [*ns* *ns*
                                                                            paths/*build-opts* opts
                                                                            viewer/doc-url (partial doc-url opts file)]
                                                                    (let [doc (eval/eval-analyzed-doc doc)]
                                                                      (assoc doc :viewer (view/doc->viewer (assoc opts
                                                                                                                  :nav-path (if (instance? java.net.URL file)
                                                                                                                              (str "'" (:ns doc))
                                                                                                                              (str file)))
                                                                                                           doc))))
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
  (build-static-app! {:paths clerk-docs :package :single-file})
  (build-static-app! {:paths ["notebooks/editor.clj"] :browse? true})
  (build-static-app! {:paths ["CHANGELOG.md" "notebooks/editor.clj"] :browse? true})
  (build-static-app! {:paths ["index.clj" "notebooks/links.md" "notebooks/rule_30.clj" "notebooks/markdown.md"] :browse? true})
  (build-static-app! {:paths ["notebooks/links.md" "notebooks/rule_30.clj" "notebooks/markdown.md"] :package :single-file})
  (build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/markdown.md"] :browse? true})
  (build-static-app! {:paths ["notebooks/viewers/**"]})
  (build-static-app! {:index "notebooks/rule_30.clj" :git/sha "bd85a3de12d34a0622eb5b94d82c9e73b95412d1" :git/url "https://github.com/nextjournal/clerk"})
  (reset! config/!resource->url @config/!asset-map)
  (swap! config/!resource->url dissoc "/css/viewer.css")

  (build-static-app! {:browse true
                      :index "notebooks/rule_30.clj"})

  (build-static-app! {:index "notebooks/document_linking.clj"
                      :paths ["notebooks/viewers/html.clj" "notebooks/rule_30.clj"]})

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
  (print (:out (sh "tree public/build")))

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
                      :git/sha "d60f5417"
                      :git/url "https://github.com/nextjournal/clerk"}))
