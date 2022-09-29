(ns nextjournal.clerk.builder
  "Clerk's Static App Builder."
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.string :as str]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.builder-ui :as builder-ui]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.view :as view]))


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
  (str/replace path #"\.(clj|cljc|md)$" ".html"))

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

(defn stdout-reporter [event]
  (doto (describe-event event)
    (print)
    (do (flush))))

(defn process-build-opts [{:as opts :keys [paths]}]
  (merge {:out-path (str "public" fs/file-separator "build")
          :bundle? true
          :browse? true
          :report-fn stdout-reporter}
         opts))

(defn write-static-app!
  "Creates a static html app of the seq of `docs`. Customizable with an `opts` map with keys:

  - `:paths` a vector of relative paths to notebooks to include in the build
  - `:bundle?` builds a single page app versus a folder with an html page for each notebook (defaults to `true`)
  - `:out-path` a relative path to a folder to contain the static pages (defaults to `\"public/build\"`)
  - `:git/sha`, `:git/url` when both present, each page displays a link to `(str url \"blob\" sha path-to-notebook)`"
  [opts docs]
  (let [{:as opts :keys [bundle? out-path browse?]} (process-build-opts opts)
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
      (browse/browse-url (-> index-html fs/absolutize .toString path-to-url-canonicalize)))
    docs))

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
  (let [{:as opts :keys [expanded-paths paths download-cache-fn upload-cache-fn bundle? report-fn]} (assoc (process-build-opts opts) :expanded-paths (-> opts :paths expand-paths))
        _ (when (empty? expanded-paths)
            (throw (ex-info "nothing to build" {:expanded-paths expanded-paths :paths paths})))
        start (System/nanoTime)
        state (mapv #(hash-map :file %) expanded-paths)
        _ (report-fn {:stage :init :state state})
        {state :result duration :time-ms} (eval/time-ms (mapv (comp (partial parser/parse-file {:doc? true}) :file) state))
        _ (report-fn {:stage :parsed :state state :duration duration})
        {state :result duration :time-ms} (eval/time-ms (mapv (comp analyzer/hash
                                                                    analyzer/build-graph) state))
        _ (report-fn {:stage :analyzed :state state :duration duration})
        _ (when download-cache-fn
            (report-fn {:stage :downloading-cache})
            (let [{duration :time-ms} (eval/time-ms (download-cache-fn state))]
              (report-fn {:stage :done :duration duration})))
        state (mapv (fn [doc idx]
                      (report-fn {:stage :building :doc doc :idx idx})
                      (let [{doc+viewer :result duration :time-ms} (eval/time-ms
                                                                    (let [doc (eval/eval-analyzed-doc doc)]
                                                                      (assoc doc :viewer (view/doc->viewer (assoc opts :inline-results? true) doc))))]
                        (report-fn {:stage :built :doc doc+viewer :duration duration :idx idx})
                        doc+viewer)) state (range))
        {state :result duration :time-ms} (eval/time-ms (write-static-app! opts state))]
    (when upload-cache-fn
      (report-fn {:stage :uploading-cache})
      (let [{duration :time-ms} (eval/time-ms (upload-cache-fn state))]
        (report-fn {:stage :done :duration duration})))
    (report-fn {:stage :finished :state state :duration duration :total-duration (eval/elapsed-ms start)})))

#_(build-static-app! {:paths (take 5 clerk-docs)})
#_(build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/viewer_api.md"] :bundle? true})
#_(build-static-app! {:paths ["index.clj" "notebooks/rule_30.clj" "notebooks/viewer_api.md"] :bundle? false})
#_(build-static-app! {:paths ["notebooks/viewers/**"]})
