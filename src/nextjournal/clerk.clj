(ns nextjournal.clerk
  "Clerk's Public API."
  (:refer-clojure :exclude [comment])
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [nextjournal.beholder :as beholder]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.builder :as builder]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.paths :as paths]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver]))

(defonce ^:private !show-filter-fn (atom nil))
(defonce ^:private !last-file (atom nil))
(defonce ^:private !watcher (atom nil))

(defn show!
  "Evaluates the Clojure source in `file-or-ns` and makes Clerk show it in the browser.

  Accepts ns using a quoted symbol or a `clojure.lang.Namespace`, calls `slurp` on all other arguments, e.g.:

  ```clj
  (nextjournal.clerk/show! \"notebooks/vega.clj\")
  (nextjournal.clerk/show! 'nextjournal.clerk.tap)
  (nextjournal.clerk/show! (find-ns 'nextjournal.clerk.tap))
  (nextjournal.clerk/show! \"https://raw.githubusercontent.com/nextjournal/clerk-demo/main/notebooks/rule_30.clj\")
  (nextjournal.clerk/show! (java.io.StringReader. \";; # Notebook from String 👋\n(+ 41 1)\"))
  ```
  "
  ([file-or-ns] (show! {} file-or-ns))
  ([opts file-or-ns]
   (if config/*in-clerk*
     ::ignored
     (try
       (webserver/set-status! {:progress 0 :status "Parsing…"})
       (let [file (cond
                    (nil? file-or-ns)
                    (throw (ex-info "`nextjournal.clerk/show!` cannot show `nil`."
                                    {:file-or-ns file-or-ns}))

                    (or (symbol? file-or-ns) (instance? clojure.lang.Namespace file-or-ns))
                    (or (some (fn [ext]
                                (io/resource (str (str/replace (namespace-munge file-or-ns) "." "/") ext)))
                              [".clj" ".cljc"])
                        (throw (ex-info (str "`nextjournal.clerk/show!` could not find a resource on the classpath for: `" (pr-str file-or-ns) "`")
                                        {:file-or-ns file-or-ns})))

                    :else
                    file-or-ns)
             doc (try (merge (webserver/get-build-opts)
                             opts
                             (when-let [path (paths/path-in-cwd file-or-ns)]
                               {:file-path path})
                             {:nav-path (webserver/->nav-path file-or-ns)}
                             (parser/parse-file file))
                      (catch java.io.FileNotFoundException e
                        (throw (ex-info (str "`nextjournal.clerk/show!` could not find the file: `" (pr-str file-or-ns) "`")
                                        {:file-or-ns file-or-ns}
                                        e)))
                      (catch Exception e
                        (throw (ex-info (str "`nextjournal.clerk/show!` could not not parse the file: `" (pr-str file-or-ns) "`")
                                        {:file file-or-ns}
                                        e))))
             _ (reset! !last-file file)
             {:keys [blob->result]} @webserver/!doc
             {:keys [result time-ms]} (eval/time-ms (binding [paths/*build-opts* (webserver/get-build-opts)]
                                                      (eval/+eval-results blob->result (assoc doc :set-status-fn webserver/set-status!))))]
         (if (:error result)
           (println (str "Clerk encountered an error evaluating '" file "' after " time-ms "ms."))
           (println (str "Clerk evaluated '" file "' in " time-ms "ms.")))
         (webserver/update-doc! result)
         (when-let [error (and (not (::skip-throw opts))
                               (:error result))]
           (throw error)))
       (catch Exception e
         (webserver/update-doc! (-> @webserver/!doc (assoc :error e) (update :ns #(or % (find-ns 'user)))))
         (throw e))))))

#_(show! "notebooks/exec_status.clj")
#_(clear-cache!)

#_(show! 'nextjournal.clerk.tap)
#_(show! (do (require 'clojure.inspector) (find-ns 'clojure.inspector)))
#_(show! "https://raw.githubusercontent.com/nextjournal/clerk-demo/main/notebooks/rule_30.clj")
#_(show! (java.io.StringReader. ";; # In Memory Notebook 👋\n(+ 41 1) (/ 1 0)"))

(defn recompute!
  "Recomputes the currently visible doc, without parsing it."
  []
  (when-not (eval/cljs? @webserver/!doc)
    (binding [*ns* (:ns @webserver/!doc)]
      (let [{:keys [result time-ms]} (eval/time-ms (eval/eval-analyzed-doc @webserver/!doc))]
        (println (str "Clerk recomputed '" @!last-file "' in " time-ms "ms."))
        (webserver/update-doc! result)))))

#_(recompute!)

(defn present
  "Presents the given value `x`.

  Transparently handles wrapped values and supports customization this way."
  [x]
  (v/present x))

(defn present!
  "Shows the given value `x` in Clerk. Returns the presented value of
  `x` that's sent to the browser."
  [x]
  (reset! @(requiring-resolve 'nextjournal.clerk.presenter/!val) x)
  (if (= (the-ns 'nextjournal.clerk.presenter)
         (:ns @webserver/!doc))
    (recompute!)
    (show! 'nextjournal.clerk.presenter))
  (-> @webserver/!doc meta :nextjournal/value :blocks peek :nextjournal/value first :nextjournal/value :nextjournal/presented))

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
          show-file? (show! {::skip-throw true} rel-path)
          @!last-file (show! {::skip-throw true} @!last-file))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public viewer api


(defn with-viewer
  "Displays `x` using the given `viewer`.

  Takes an optional second `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/render-opts`: a map argument that will be passed as a secong arg to the viewers `:render-fn`"
  ([viewer x] (with-viewer viewer {} x))
  ([viewer viewer-opts x] (v/with-viewer viewer viewer-opts x)))


(defn with-viewers
  [viewers x]
  (v/with-viewers viewers x))

(def default-viewers
  "Clerk's default viewers."
  v/default-viewers)

(defn get-default-viewers
  "Gets Clerk's current set of default viewers.

  Use `(reset-viewers! :default ,,,)` to change them."
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
  "Resets the viewers associated with the given `scope` to `viewers`.

  When no `scope` is given, resets the viewers for the current namespace.
  Passsing `:default` resets the global default viewers in Clerk."
  ([viewers] (v/reset-viewers! viewers))
  ([scope viewers] (v/reset-viewers! scope viewers)))


(defn add-viewers!
  "Adds `viewers` to the viewers associated with the current namespace."
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
  ([wrapped-value]
   (v/mark-preserve-keys wrapped-value))
  ([preserve-keys-fn wrapped-value]
   (v/mark-preserve-keys preserve-keys-fn wrapped-value)))

(defn preserve-keys
  "Takes a `preserve-keys-fn` (normally a set) and returns a function
  usabable as a `:transform-fn` that preserves all keys and values for
  which `(preserve-keys-fn k)` returns a truthy value."
  [preserve-keys-fn]
  (v/preserve-keys preserve-keys-fn))

(defn resolve-aliases
  "Resolves aliases in `form` using the aliases from `*ns*`. Meant to be used on `:render-fn`s."
  [form]
  (v/resolve-aliases form))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public convenience api


(defn html
  "Displays `x` using the html-viewer. Supports HTML and SVG as strings or hiccup.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/render-opts`: a map argument that will be passed as a secong arg to the viewers `:render-fn`"
  ([x] (v/html x))
  ([viewer-opts x] (v/html viewer-opts x)))

(defn md
  "Displays `x` with the markdown viewer.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/render-opts`: a map argument that will be passed as a secong arg to the viewers `:render-fn`"
  ([x] (v/md x))
  ([viewer-opts x] (v/md viewer-opts x)))

(defn plotly
  "Displays `x` with the plotly viewer.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/render-opts`: a map argument that will be passed as a secong arg to the viewers `:render-fn`"
  ([x] (v/plotly x))
  ([viewer-opts x] (v/plotly viewer-opts x)))

(defn vl
  "Displays `x` with the vega embed viewer, supporting both vega-lite and vega.

  `x` should be the standard vega view description map, accepting the following addtional keys:
  * `:embed/callback` a function to be called on the vega-embed object after creation.
  * `:embed/opts` a map of vega-embed options (see https://github.com/vega/vega-embed#options)

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/render-opts`: a map argument that will be passed as a secong arg to the viewers `:render-fn`"
  ([x] (v/vl x))
  ([viewer-opts x] (v/vl viewer-opts x)))

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
  * `:nextjournal.clerk/render-opts`: a map argument that will be passed as a secong arg to the viewers `:render-fn`"
  ([xs] (v/table xs))
  ([viewer-opts xs] (v/table viewer-opts xs)))

(defn row
  "Displays `xs` as rows.

  Treats the first argument as `viewer-opts` if it is a map but not a `wrapped-value?`.

  The `viewer-opts` map can contain the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/render-opts`: a map argument that will be passed as a secong arg to the viewers `:render-fn`"
  [& xs] (apply v/row xs))

(defn col
  "Displays `xs` as columns.

  Treats the first argument as `viewer-opts` if it is a map but not a `wrapped-value?`.

  The `viewer-opts` map can contain the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/render-opts`: a map argument that will be passed as a secong arg to the viewers `:render-fn`"
  [& xs] (apply v/col xs))

(defn tex
  "Displays `x` as LaTeX using KaTeX.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/render-opts`: a map argument that will be passed as a secong arg to the viewers `:render-fn`"
  ([x] (v/tex x))
  ([viewer-opts x] (v/tex viewer-opts x)))

(defn hide-result
  "Deprecated, please put `^{:nextjournal.clerk/visibility {:result :hide}}` metadata on the form instead."
  {:deprecated "0.10"}
  ([x] #_:clj-kondo/ignore (v/hide-result x))
  ([viewer-opts x] #_:clj-kondo/ignore (v/hide-result viewer-opts x)))

(defn image
  "Creates a `java.awt.image.BufferedImage` from `url`, which can be a `java.net.URL` or a string, and
  displays it using the `buffered-image-viewer`."
  ([url] (v/image url))
  ([viewer-opts url] (v/image viewer-opts url)))

(defn caption
  "Displays `content` with `text` as caption below it."
  [text content]
  (v/caption text content))

(defn fragment
  "A utility function to splice the given `xs` into individual results.

  Useful when prgrammatically generating content."
  [& xs]
  (apply v/fragment xs))

(defn code
  "Displays `x` as syntax highlighted Clojure code.

  A string is shown as-is, any other arg will be pretty-printed via `clojure.pprint/pprint`.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/render-opts`: a map argument that will be passed as a secong arg to the viewers `:render-fn`"
  ([code-string-or-form] (v/code code-string-or-form))
  ([viewer-opts code-string-or-form] (v/code viewer-opts code-string-or-form)))

(defn eval-cljs-str
  "Evaluates the given ClojureScript `code-string` in the browser."
  ([code-string] (v/eval-cljs-str code-string))
  ([opts code-string] (v/eval-cljs-str opts code-string)))

(defn eval-cljs
  "Evaluates the given ClojureScript forms in the browser."
  [& forms]
  (apply v/eval-cljs forms))

(defn read-js-literal
  "Data reader for the `#js` literal.

  Use it with the following if you want to use `#js` in `:render-fn`s.

  (set! *data-readers* (assoc *data-readers* 'js read-js-literal))"
  [data]
  (cond
    (vector? data) (list* 'cljs.core/array data)
    (map? data) (list* 'cljs.core/js-obj (interleave (map name (keys data)) (vals data)))))

(def notebook
  "Experimental notebook viewer. You probably should not use this."
  (partial with-viewer (:name v/notebook-viewer)))

(defn doc-url [& args] (apply v/doc-url args))

(defmacro comment
  "Evaluates the expressions in `body` showing the results in Clerk.

  Does nothing outside of Clerk, like `clojure.core/comment`."
  [& body]
  (when nextjournal.clerk.config/*in-clerk*
    `(nextjournal.clerk/fragment ~(vec body))))

(defmacro example
  "Evaluates the expressions in `body` showing code next to results in Clerk.

  Does nothing outside of Clerk, like `clojure.core/comment`."
  [& body]
  (when nextjournal.clerk.config/*in-clerk*
    `(nextjournal.clerk/with-viewer v/examples-viewer
       (mapv (fn [form# val#] {:form form# :val val#}) ~(mapv (fn [x#] `'~x#) body) ~(vec body)))))

(defn halt-watcher!
  "Halts the filesystem watcher when active."
  []
  (when-let [{:keys [watcher paths]} @!watcher]
    (println "Stopping old watcher for paths" (pr-str paths))
    (beholder/stop watcher)
    (reset! !watcher nil)))

(defn deprecate+migrate-opts [{:as opts :keys [bundle?]}]
  (when (contains? opts :bundle?)
    (println "\nThe `:bundle(?)` option is deprecated and will be dropped in 1.0.0. Use `:package :single-file` instead.\n"))
  (-> opts
      (dissoc :bundle?)
      (cond-> bundle?
        (assoc :package :single-file))))

(defn ^:private normalize-opts [opts]
  (deprecate+migrate-opts
   (set/rename-keys opts {:bundle :bundle?, :browse :browse?, :dashboard :dashboard? :compile-css :compile-css? :ssr :ssr? :exclude-js :exclude-js?})))

(defn ^:private started-via-bb-cli? [opts]
  (contains? (meta opts) :org.babashka/cli))

(defn serve!
  "Main entrypoint to Clerk taking an configurations map.

  Options:
  - `:host` for the webserver to listen on, defaulting to `\"localhost\"`
  - `:port` for the webserver to listen on, defaulting to `7777`
  - `:render-nrepl` opt into starting a render nREPL server into Clerk's render environment running in the browser, pass `{}` to start it on the default port or pass a custom `:port` number.
  - `:browse` will open Clerk in the default browser after it's been started
  - `:watch-paths` that Clerk will watch for file system events and show any changed file
  - `:show-filter-fn` to restrict when to re-evaluate or show a notebook as a result of file system event. Useful for e.g. pinning a notebook. Will be called with the string path of the changed file.
  - `:paths`     - restricts serving to the given paths, supports glob patterns. Will disable Clerk's homepage when set.
  - `:paths-fn`  - a symbol resolving to a 0-arity function returning computed paths.
  - `:index`     - path to a file to override Clerk's default index, will be added to paths.

  When both `:paths` and `:paths-fn` are given, `:paths` takes precendence.

  Can be called multiple times and Clerk will happily serve you according to the latest config."
  {:org.babashka/cli {:spec {:watch-paths {:desc "Paths on which to watch for changes and show a changed document."
                                           :coerce []}
                             :paths {:desc "Restricts serving to the given paths, supports glob patterns. Will disable Clerk's homepage when set."
                                     :coerce []}
                             :paths-fn {:desc "Symbol resolving to a 0-arity function returning computed paths."
                                        :coerce :symbol}
                             :host {:desc "Host or ip for the webserver to listen on, defaults to \"locahost\"."}
                             :port {:desc "Port number for the webserver to listen on, defaults to 7777."
                                    :coerce :number}
                             :index {:desc "Override the name of the index file (default \"index.clj|md\", will be added to paths."}
                             :show-filter-fn {:desc "Symbol resolving to a fn to restrict when to show a notebook as a result of file system event."
                                              :coerce :symbol}
                             :browse {:desc "Opens the browser on boot when set."
                                      :coerce :boolean}}
                      :order [:host :port :browse :watch-paths :show-filter-fn :paths :paths-fn :index]}}
  [config]
  (if (:help config)
    (if-let [format-opts (and (started-via-bb-cli? config) (requiring-resolve 'babashka.cli/format-opts))]
      (println "Start the Clerk webserver with an optional a file watcher.\n\nOptions:"
               (str "\n" (format-opts (-> #'serve! meta :org.babashka/cli))))
      (println (-> #'serve! meta :doc)))
    (let [{:as normalized-config
           :keys [browse? watch-paths show-filter-fn]} (normalize-opts config)]
      (webserver/serve! normalized-config)
      (reset! !show-filter-fn show-filter-fn)
      (halt-watcher!)
      (when (seq watch-paths)
        (println "Starting new watcher for paths" (pr-str watch-paths))
        (reset! !watcher {:paths watch-paths
                          :watcher (apply beholder/watch #(file-event %) watch-paths)}))
      (when browse?
        (try
          (webserver/browse!)
          (catch UnsupportedOperationException e
            (binding [*out* *err*]
              (println "Clerk could not open the browser:" (.getMessage e))))))))
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
  - `:paths`     a vector of relative paths to notebooks to include in the build
  - `:paths-fn`  a symbol resolving to a 0-arity function returning computed paths
  - `:index`     a string allowing to override the name of the index file, will be added to `:paths`

  Passing at least one of the above is required. When both `:paths` and `:paths-fn` are given, `:paths` takes precendence.

  - `:package`     a keyword to specify how the static build should be bundled:
    - `:directory` (default) constructs a distinct html file for each document in `:paths`
    - `:single-file` bundles all documents into a single html file
  - `:bundle`      [DEPRECATED use :package :single-file instead] if true results in a single self-contained html file including inlined images
  - `:compile-css` if true compiles css file containing only the used classes
  - `:ssr`         if true runs react server-side-rendering and includes the generated markup in the html
  - `:browse`      if true will open browser with the built file on success
  - `:dashboard`   if true will start a server and show a rich build report in the browser (use with `:bundle` to open browser)
  - `:out-path`    a relative path to a folder to contain the static pages (defaults to `\"public/build\"`)
  - `:git/sha`, `:git/url` when both present, each page displays a link to `(str url \"blob\" sha path-to-notebook)`
  "
  {:org.babashka/cli {:spec {:paths {:desc "Paths to notebooks toc include in the build, supports glob patterns."
                                     :coerce []}
                             :paths-fn {:desc "Symbol resolving to a 0-arity function returning computed paths."
                                        :coerce :symbol}
                             :index {:desc "Override the name of the index file (default `index.clj|md`), will be added to paths."}
                             :package {:desc "Indicates how the static build should be packaged"
                                       :coerce :keyword
                                       :default :directory
                                       :validate #{:directory :single-file}}
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

#_(build! {:paths ["notebooks/eval_cljs.clj"]})

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

;; And, as is the culture of our people, a comment block containing
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
  (show! "notebooks/hello.cljs")
  (show! "notebooks/conditional_read.cljc")
  (show! "src/nextjournal/clerk/analyzer.clj")
  (show! "src/nextjournal/clerk.clj")
  (show! "notebooks/cherry.clj")

  (show! "notebooks/test.clj")

  (serve! {:port 7777 :watch-paths ["notebooks"]})

  ;; Clear cache
  (clear-cache!)
  (halt!)
  )

(comment
  (with-cache 1)
  )
