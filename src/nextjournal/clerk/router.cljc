(ns nextjournal.clerk.router
  "Url paths of a static build for the `:fetch-edn` router.
  The router runs in the browser only.
  This namespace is cljc so JVM tests can check its paths against the links the builder writes."
  (:require [clojure.string :as str]))

(defn strip-index-html [path]
  (str/replace path #"/(index\.html)?$" ""))

(defn build-root
  "Returns the root url path of the static build with a trailing slash, or nil if pathname does not end in current-path.
  pathname is the decoded url path of the current page.
  current-path is the doc path of the current page."
  [pathname current-path]
  (let [path (strip-index-html pathname)]
    (cond (empty? current-path) (str path "/")
          (str/ends-with? path (str "/" current-path)) (subs path 0 (- (count path) (count current-path))))))

(defn url-path->doc-path
  "Returns the doc path of the decoded url path pathname, or nil if pathname is not a doc of this build.
  paths is the set of doc paths of the build, or nil if unknown."
  [{:keys [root paths]} pathname]
  (let [path (str (strip-index-html pathname) "/")]
    (when (and root (str/starts-with? path root))
      (let [doc-path (str/replace (subs path (count root)) #"/$" "")]
        (when (or (nil? paths) (contains? paths doc-path))
          doc-path)))))

(defn doc-path->edn-path [root doc-path]
  (str root (if (empty? doc-path) "index" doc-path) ".edn"))

(defn doc-path->url-path [root doc-path]
  ;; relative links in a static build assume the trailing slash
  (str root doc-path (when (seq doc-path) "/")))
