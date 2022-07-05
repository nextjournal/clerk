(ns nextjournal.clerk
  "Clerk's Public API."
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
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

(defn clear-cache! []
  (swap! webserver/!doc dissoc :blob->result)
  (if (fs/exists? config/cache-dir)
    (do
      (fs/delete-tree config/cache-dir)
      (prn :cache-dir/deleted config/cache-dir))
    (prn :cache-dir/does-not-exist config/cache-dir)))

#_(clear-cache!)
#_(blob->result @nextjournal.clerk.webserver/!doc)


(defn parse-file [file]
  (parser/parse-file {:doc? true} file))

#_(parse-file "notebooks/elements.clj")
#_(parse-file "notebooks/visibility.clj")

#_(analyzer/build-graph (parse-file "notebooks/test123.clj"))

(def eval-doc eval/eval-doc)
(def eval-file eval/eval-file)
(def eval-string eval/eval-string)

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
            {:keys [result time-ms]} (eval/time-ms (eval/+eval-results blob->result doc))]
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
    (let [{:keys [result time-ms]} (eval/time-ms (eval/eval-analyzed-doc @webserver/!doc))]
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
(def row            v/row)
(def col            v/col)
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
(def update-val     v/update-val)
(def mark-presented v/mark-presented)
(def mark-preserve-keys v/mark-preserve-keys)

(defmacro example
  [& body]
  (when nextjournal.clerk.config/*in-clerk*
    `(clerk/with-viewer v/examples-viewer
       (mapv (fn [form# val#] {:form form# :val val#}) ~(mapv (fn [x#] `'~x#) body) ~(vec body)))))


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

(def valuehash analyzer/valuehash)

(def build-static-app! builder/build-static-app!)

(def eval-cljs-viewer
  {:pred #(instance? nextjournal.clerk.viewer.ViewerEval %)
   ;; NOTE: this is implementation detail that depends on how SCI is evaluating
   ;; code in clerk and might change in the future!
   :render-fn '(fn [code]
                 (v/html
                  [v/inspect-paginated
                   (binding [*ns* *ns*]
                     (load-string code))]))})

(add-viewers! [eval-cljs-viewer])

(defn eval-cljs [string-or-resource-or-url]
  (with-viewer eval-cljs-viewer string-or-resource-or-url))

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

  ;; Clear cache
  (clear-cache!)

  )
