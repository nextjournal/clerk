(ns nextjournal.clerk
  "Clerk's Public API."
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [nextjournal.beholder :as beholder]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.builder :as builder]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver]))

(defonce ^:private !show-filter-fn (atom nil))
(defonce ^:private !last-file (atom nil))
(defonce ^:private !watcher (atom nil))


(defn show!
  "Evaluates the Clojure source in `file-or-ns` and makes Clerk show it in the browser.

  Accepts ns using a quoted symbol or a `clojure.lang.Namespace`, calls `slurp` on all other arguments, e.g.:

  (nextjournal.clerk/show! \"notebooks/vega.clj\")
  (nextjournal.clerk/show! 'nextjournal.clerk.tap)
  (nextjournal.clerk/show! (find-ns 'nextjournal.clerk.tap))
  (nextjournal.clerk/show! \"https://raw.githubusercontent.com/nextjournal/clerk-demo/main/notebooks/rule_30.clj\")
  (nextjournal.clerk/show! (java.io.StringReader. \";; # Notebook from String 👋\n(+ 41 1)\"))
  "
  [file-or-ns]
  (if config/*in-clerk*
    ::ignored
    (try
      (let [file (cond
                   (nil? file-or-ns)
                   (throw (ex-info (str "`nextjournal.clerk/show!` cannot show `nil`.")
                                   {:file-or-ns file-or-ns}))

                   (or (symbol? file-or-ns) (instance? clojure.lang.Namespace file-or-ns))
                   (or (some (fn [ext]
                               (io/resource (str (str/replace (namespace-munge file-or-ns) "." "/") ext)))
                             [".clj" ".cljc"])
                       (throw (ex-info (str "`nextjournal.clerk/show!` could not find a resource on the classpath for: `" (pr-str file-or-ns) "`")
                                       {:file-or-ns file-or-ns})))

                   :else
                   file-or-ns)
            doc (try (parser/parse-file {:doc? true} file)
                     (catch java.io.FileNotFoundException _e
                       (throw (ex-info (str "`nextjournal.clerk/show!` could not find the file: `" (pr-str file-or-ns) "`")
                                       {:file-or-ns file-or-ns}))))
            _ (reset! !last-file file)
            {:keys [blob->result]} @webserver/!doc
            {:keys [result time-ms]} (eval/time-ms (eval/+eval-results blob->result doc))]
        (println (str "Clerk evaluated '" file "' in " time-ms "ms."))
        (webserver/update-doc! result))
      (catch Exception e
        (webserver/show-error! e)
        (throw e)))))

#_(show! 'nextjournal.clerk.tap)
#_(show! (do (require 'clojure.inspector) (find-ns 'clojure.inspector)))
#_(show! "https://raw.githubusercontent.com/nextjournal/clerk-demo/main/notebooks/rule_30.clj")
#_(show! (java.io.StringReader. ";; # In Memory Notebook 👋\n(+ 41 1)"))

(defn recompute!
  "Recomputes the currently visible doc, without parsing it."
  []
  (binding [*ns* (:ns @webserver/!doc)]
    (let [{:keys [result time-ms]} (eval/time-ms (eval/eval-analyzed-doc @webserver/!doc))]
      (println (str "Clerk recomputed '" @!last-file "' in " time-ms "ms."))
      (webserver/update-doc! result))))

#_(recompute!)

(defn ^:private supported-file?
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


(defn ^:private file-event [{:keys [type path]}]
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


(defn with-viewer
  "Displays `x` using the given `viewer`.

  Takes an optional second `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`"
  ([viewer x] (with-viewer viewer {} x))
  ([viewer viewer-opts x] (v/with-viewer viewer viewer-opts x)))


(defn with-viewers
  [viewers x]
  (v/with-viewers viewers x))

(defn get-default-viewers
  "Gets Clerk's default viewers."
  []
  (v/get-default-viewers))

(defn add-viewers
  ([added-viewers] (v/add-viewers added-viewers))
  ([viewers added-viewers] (v/add-viewers viewers added-viewers)))

(defn update-viewers
  "Takes `viewers` and a `select-fn->update-fn` map returning updated
  viewers with each viewer matching `select-fn` will by updated using
  the function in `update-fn`."
  [viewers select-fn->update-fn]
  (v/update-viewers viewers select-fn->update-fn))


(defn reset-viewers!
  "Resets the viewers associated with the current `*ns*` to `viewers`."
  [viewers] (v/reset-viewers! *ns* viewers))


(defn add-viewers!
  "Adds `viewers` to the viewers associated with the current `*ns*`."
  [viewers] (v/add-viewers! viewers))


(defn ^{:deprecated "0.8"} set-viewers!
  "Deprecated, please use `add-viewers!` instead."
  [viewers]
  (binding [*out* *err*]
    (println "`set-viewers!` has been deprecated, please use `add-viewers!` or `reset-viewers!` instead."))
  (add-viewers! viewers))


(defn ->value
  "Takes `x` and returns the `:nextjournal/value` from it, or otherwise `x` unmodified."
  [x]
  (v/->value x))


(defn update-val
  "Take a function `f` and optional `args` and returns a function to update only the `:nextjournal/value` inside a wrapped-value."
  [f & args]
  (apply v/update-val f args))


(defn mark-presented
  "Marks the given `wrapped-value` so that it will be passed unmodified to the browser."
  [wrapped-value]
  (v/mark-presented wrapped-value))


(defn mark-preserve-keys
  "Marks the given `wrapped-value` so that the keys will be passed unmodified to the browser."
  [wrapped-value]
  (v/mark-preserve-keys wrapped-value))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public convenience api


(defn html
  "Displays `x` using the html-viewer. Supports HTML and SVG as strings or hiccup.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`"
  ([x] (html {} x))
  ([viewer-opts x] (with-viewer v/html-viewer viewer-opts x)))

(defn md
  "Displays `x` with the markdown viewer.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`"
  ([x] (md {} x))
  ([viewer-opts x] (with-viewer v/markdown-viewer viewer-opts x)))

(defn plotly
  "Displays `x` with the plotly viewer.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`"
  ([x] (plotly {} x))
  ([viewer-opts x] (with-viewer v/plotly-viewer viewer-opts x)))

(defn vl
  "Displays `x` with the vega embed viewer, supporting both vega-lite and vega.

  `x` should be the standard vega view description map, accepting the following addtional keys:
  * `:embed/callback` a function to be called on the vega-embed object after creation.
  * `:embed/opts` a map of vega-embed options (see https://github.com/vega/vega-embed#options)

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`"
  ([x] (vl {} x))
  ([viewer-opts x] (with-viewer v/vega-lite-viewer viewer-opts x)))

(defn use-headers
  "Treats the first element of the seq `xs` as a header for the table.

  Meant to be used in combination with `table`."
  [xs]
  (v/use-headers xs))

(defn table
  "Displays `xs` using the table viewer.

  Performs normalization on the data, supporting:
  * seqs of maps
  * maps of seqs
  * seqs of seqs

  If you want a header for seqs of seqs use `use-headers`.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`"
  ([xs] (table {} xs))
  ([viewer-opts xs] (with-viewer v/table-viewer viewer-opts xs)))

(defn row
  "Displays `xs` as rows.

  Treats the first argument as `viewer-opts` if it is a map but not a `wrapped-value?`.

  The `viewer-opts` map can contain the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`"
  [& xs] (apply v/with-viewer-extracting-opts v/row-viewer xs))

(defn col
  "Displays `xs` as columns.

  Treats the first argument as `viewer-opts` if it is a map but not a `wrapped-value?`.

  The `viewer-opts` map can contain the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`"
  [& xs] (apply v/with-viewer-extracting-opts v/col-viewer xs))

(defn tex
  "Displays `x` as LaTeX using KaTeX.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`"
  ([x] (tex {} x))
  ([viewer-opts x] (with-viewer v/katex-viewer viewer-opts x)))

(defn hide-result
  "Deprecated, please put `^{:nextjournal.clerk/visibility {:result :hide}}` metadata on the form instead."
  {:deprecated "0.10"}
  ([x] (v/print-hide-result-deprecation-warning) (with-viewer v/hide-result-viewer {} x))
  ([viewer-opts x] (v/print-hide-result-deprecation-warning) (with-viewer v/hide-result-viewer viewer-opts x)))


(defn code
  "Displays `x` as syntax highlighted Clojure code.

  A string is shown as-is, any other arg will be pretty-printed via `clojure.pprint/pprint`.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`"
  ([code-string-or-form] (code {} code-string-or-form))
  ([viewer-opts code-string-or-form] (with-viewer v/code-viewer viewer-opts code-string-or-form)))

(defn eval-cljs-str
  "Evaluates the given ClojureScript `code-string` in the browser."
  [code-string]
  (v/eval-cljs-str code-string))

(defn eval-cljs
  "Evaluates the given ClojureScript forms in the browser."
  [& forms]
  (apply v/eval-cljs forms))

(def notebook
  "Experimental notebook viewer. You probably should not use this."
  (partial with-viewer (:name v/notebook-viewer)))

(defn doc-url [path] (v/doc-url path))

(defmacro example
  "Evaluates the expressions in `body` showing code next to results in Clerk.

  Does nothing outside of Clerk, like `clojure.core/comment`."
  [& body]
  (when nextjournal.clerk.config/*in-clerk*
    `(clerk/with-viewer v/examples-viewer
       (mapv (fn [form# val#] {:form form# :val val#}) ~(mapv (fn [x#] `'~x#) body) ~(vec body)))))

(defn file->viewer
  "Evaluates the given `file` and returns it's viewer representation."
  ([file] (file->viewer {:inline-results? true} file))
  ([opts file] (view/doc->viewer opts (eval/eval-file file))))

#_(file->viewer "notebooks/rule_30.clj")

(defn halt-watcher!
  "Halts the filesystem watcher when active."
  []
  (when-let [{:keys [watcher paths]} @!watcher]
    (println "Stopping old watcher for paths" (pr-str paths))
    (beholder/stop watcher)
    (reset! !watcher nil)))

(defn ^:private normalize-opts [opts]
  (set/rename-keys opts #_(into {} (map (juxt identity #(keyword (str (name %) "?")))) [:bundle :browse :dashboard])
                   {:bundle :bundle?, :browse :browse?, :dashboard :dashboard? :compile-css :compile-css? :ssr :ssr?}))

(defn ^:private started-via-bb-cli? [opts]
  (contains? (meta opts) :org.babashka/cli))

(defn serve!
  "Main entrypoint to Clerk taking an configurations map.

  Will obey the following optional configuration entries:

  * a `:port` for the webserver to listen on, defaulting to `7777`
  * `:browse?` will open Clerk in a browser after it's been started
  * a sequence of `:watch-paths` that Clerk will watch for file system events and show any changed file
  * a `:show-filter-fn` to restrict when to re-evaluate or show a notebook as a result of file system event. Useful for e.g. pinning a notebook. Will be called with the string path of the changed file.

  Can be called multiple times and Clerk will happily serve you according to the latest config."
  {:org.babashka/cli {:spec {:watch-paths {:desc "Paths on which to watch for changes and show a changed document."
                                           :coerce []}
                             :port {:desc "Port number for the webserver to listen on, defaults to 7777."
                                    :coerce :number}
                             :show-filter-fn {:desc "Symbol resolving to a fn to restrict when to show a notebook as a result of file system event."
                                              :coerce :symbol}
                             :browse {:desc "Opens the browser on boot when set."
                                      :coerce :boolean}}
                      :order [:watch-paths :port :show-filter-fn :browse]}}
  [config]
  (if (:help config)
    (if-let [format-opts (and (started-via-bb-cli? config) (requiring-resolve 'babashka.cli/format-opts))]
      (println "Start the Clerk webserver with an optional a file watcher.\n\nOptions:"
               (str "\n" (format-opts (-> #'serve! meta :org.babashka/cli))))
      (println (-> #'serve! meta :doc)))
    (let [{:as _normalized-config
           :keys [browse? watch-paths port show-filter-fn]
           :or {port 7777}} (normalize-opts config)]
      (webserver/serve! {:port port})
      (reset! !show-filter-fn show-filter-fn)
      (halt-watcher!)
      (when (seq watch-paths)
        (println "Starting new watcher for paths" (pr-str watch-paths))
        (reset! !watcher {:paths watch-paths
                          :watcher (apply beholder/watch #(file-event %) watch-paths)}))
      (when browse?
        (browse/browse-url (str "http://localhost:" port)))))
  config)

#_(serve! (with-meta {:help true} {:org.babashka/cli {}}))

(defn halt!
  "Stops the Clerk webserver and file watcher."
  []
  (webserver/halt!)
  (halt-watcher!))

#_(serve! {})
#_(serve! {:browse? true})
#_(serve! {:watch-paths ["src" "notebooks"]})
#_(serve! {:watch-paths ["src" "notebooks"] :show-filter-fn #(clojure.string/starts-with? % "notebooks")})

(def valuehash analyzer/valuehash)

(defn build!
  "Creates a static html build from a collection of notebooks.

  Options:
  - `:paths`     - a vector of relative paths to notebooks to include in the build
  - `:paths-fn`  - a symbol resolving to a 0-arity function returning computed paths
  - `:index`     - a string allowing to override the name of the index file, will be added to `:paths`

  Passing at least one of the above is required. When both `:paths`
  and `:paths-fn` are given, `:paths` takes precendence.

  - `:bundle`      - if true results in a single self-contained html file including inlined images
  - `:compile-css` - if true compiles css file containing only the used classes
  - `:ssr`         - if true runs react server-side-rendering and includes the generated markup in the html
  - `:browse`      - if true will open browser with the built file on success
  - `:dashboard`   - if true will start a server and show a rich build report in the browser (use with `:bundle` to open browser)
  - `:out-path`  - a relative path to a folder to contain the static pages (defaults to `\"public/build\"`)
  - `:git/sha`, `:git/url` - when both present, each page displays a link to `(str url \"blob\" sha path-to-notebook)`
  "
  {:org.babashka/cli {:spec {:paths {:desc "Paths to notebooks toc include in the build, supports glob patterns."
                                     :coerce []}
                             :paths-fn {:desc "Symbol resolving to a 0-arity function returning computed paths."
                                        :coerce :symbol}
                             :index {:desc "Override the name of the index file (default `index.clj|md`), will be added to paths."}
                             :bundle {:desc "Flag to build a self-contained html file inlcuding inlined images"}
                             :browse {:desc "Opens the browser on boot when set."}
                             :dashboard {:desc "Flag to serve a dashboard with the build progress."}
                             :out-path {:desc "Path to an build output folder, defaults to \"public/build\"."}
                             :ssr {:desc "Flag to run server-side-rendering to include pre-rendered markup in html output."}
                             :compile-css {:desc "Flag to run tailwindcss to compile a minimal stylesheet containing only the used classes."}
                             :git/sha {:desc "Git sha to use for the backlink."}
                             :git/url {:desc "Git url to use for the backlink."}}
                      :order [:paths :paths-fn :index :browse :dashbaord :compile-css :ssr :bundle :out-path :git/sha :git/url]}}
  [build-opts]
  (if (:help build-opts)
    (if-let [format-opts (and (started-via-bb-cli? build-opts) (requiring-resolve 'babashka.cli/format-opts))]
      (println "Start the Clerk webserver with an optional a file watcher.\n\nOptions:"
               (str "\n" (format-opts (-> #'build! meta :org.babashka/cli))))
      (println (-> #'build! meta :doc)))
    (let [{:as build-opts-normalized :keys [dashboard?]} (normalize-opts build-opts)]
      (when (and dashboard? (not @webserver/!server))
        (serve! build-opts-normalized))
      (builder/build-static-app! build-opts-normalized))))

#_(build! (with-meta {:help true} {:org.babashka/cli {}}))

(defn build-static-app! {:deprecated "0.11"} [build-opts]
  (binding [*out* *err*] (println "`build-static-app!` has been deprecated, please use `build!` instead."))
  (build! build-opts))

(defn clear-cache!
  "Clears the in-memory and file-system caches when called with no arguments.

  Clears the cache for a single result identitfied by `sym-or-form` argument which can be:
  * a symbol representing the var name (qualified or not)
  * the form of an anonymous expression"
  ([]
   (swap! webserver/!doc dissoc :blob->result)
   (if (fs/exists? config/cache-dir)
     (do (fs/delete-tree config/cache-dir)
         (prn :cache-dir/deleted config/cache-dir))
     (prn :cache-dir/does-not-exist config/cache-dir)))
  ([sym-or-form]
   (if-let [{:as block :keys [id result]} (first (analyzer/find-blocks @webserver/!doc sym-or-form))]
     (let [{:nextjournal/keys [blob-id]} result
           cache-file (fs/file config/cache-dir (str "@" blob-id))
           cached-in-memory? (contains? (:blob->result @webserver/!doc) blob-id)
           cached-on-fs? (fs/exists? cache-file)]
       (if-not (or cached-in-memory? cached-on-fs?)
         (prn :cache/not-cached {:id id})
         (do (swap! webserver/!doc update :blob->result dissoc blob-id)
             (fs/delete-if-exists cache-file)
             (prn :cache/removed {:id id :cached-in-memory? cached-in-memory? :cached-on-fs? cached-on-fs?}))))
     (prn :cache/no-block-found {:sym-or-form sym-or-form}))))

#_(clear-cache!)
#_(clear-cache! 'foo)
#_(clear-cache! '(rand-int 1000))
#_(blob->result @nextjournal.clerk.webserver/!doc)

(defmacro with-cache
  "An expression evaluated with Clerk's caching."
  [form]
  `(let [result# (-> ~(v/->edn form) eval/eval-string :blob->result first val :nextjournal/value)]
     result#))

#_(with-cache (do (Thread/sleep 4200) 42))

(defmacro defcached
  "Like `clojure.core/def` but with Clerk's caching of the value."
  [name expr]
  `(let [result# (-> ~(v/->edn expr) eval/eval-string :blob->result first val :nextjournal/value)]
     (def ~name result#)))

#_(defcached my-expansive-thing
    (do (Thread/sleep 4200) 42))

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
  (show! "src/nextjournal/clerk/analyzer.clj")
  (show! "src/nextjournal/clerk.clj")

  (show! "notebooks/test.clj")

  (serve! {:port 7777 :watch-paths ["notebooks"]})

  ;; Clear cache
  (clear-cache!)
  )
