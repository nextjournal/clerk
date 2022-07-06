(ns nextjournal.clerk
  "Clerk's Public API."
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.string :as str]
            [nextjournal.beholder :as beholder]
            [nextjournal.clerk.builder :as builder]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver]))

(defonce ^:private !show-filter-fn (atom nil))
(defonce ^:private !last-file (atom nil))
(defonce ^:private !watcher (atom nil))

(defn show!
  "Evaluates the Clojure source in `file` and makes Clerk show it in the browser."
  [file]
  (if config/*in-clerk*
    ::ignored
    (try
      (reset! !last-file file)
      (let [doc (parser/parse-file {:doc? true} file)
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
(def load-cljs-string v/load-cljs-string)

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
  "Stops the Clerk webserver and file watcher."
  []
  (webserver/halt!)
  (halt-watcher!))

#_(serve! {})
#_(serve! {:browse? true})
#_(serve! {:watch-paths ["src" "notebooks"]})
#_(serve! {:watch-paths ["src" "notebooks"] :show-filter-fn #(clojure.string/starts-with? % "notebooks")})

(def valuehash analyzer/valuehash)

(def build-static-app! builder/build-static-app!)

(defn clear-cache!
  "Clears the in-memory and file-system caches."
  []
  (swap! webserver/!doc dissoc :blob->result)
  (if (fs/exists? config/cache-dir)
    (do
      (fs/delete-tree config/cache-dir)
      (prn :cache-dir/deleted config/cache-dir))
    (prn :cache-dir/does-not-exist config/cache-dir)))

#_(clear-cache!)
#_(blob->result @nextjournal.clerk.webserver/!doc)

(defmacro with-cache
  "An expression evaluated with Clerk's caching."
  [form]
  `(let [result# (-> ~(pr-str form) eval/eval-string :blob->result first val)]
     result#))

#_(with-cache (do (Thread/sleep 4200) 42))

(defmacro defcached
  "Like `clojure.core/def` but with Clerk's caching of the value."
  [name expr]
  `(let [result# (-> ~(pr-str expr) eval/eval-string :blob->result first val)]
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

  ;; Clear cache
  (clear-cache!)

  )
