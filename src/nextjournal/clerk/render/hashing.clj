(ns nextjournal.clerk.render.hashing
  "Computes a hash based for Clerk's render cljs bundle."
  {:no-doc true}
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.dejavu :as djv]
            [nextjournal.cas-client.api :as cas]))

(def output-dirs ["resources/public/ui"
                  "resources/public/build"])

(defn file-set [base-dir]
  (reduce into
          []
          [(mapv #(fs/file base-dir %) ["deps.edn"
                                        "render/deps.edn"
                                        "shadow-cljs.edn"
                                        "yarn.lock"])
           (djv/cljs-files (mapv #(fs/file base-dir %) ["src" "resources"]))]))

#_(file-set (fs/file "."))
#_(System/setProperty "nextjournal.dejavu.debug" "1")

(defn front-end-hash []
  (let [base-dir (let [resource (io/resource "nextjournal/clerk.clj")]
                   (when (= "file" (.getProtocol resource))
                     (-> (fs/file resource) fs/parent fs/parent fs/parent)))]
    (when-not base-dir
      (throw (ex-info "Clerk could note compute `font-end-hash` for cljs bundle." {:base-dir base-dir})))
    (str (djv/file-set-hash base-dir (file-set base-dir)))))

(def ^:private prefix "clerk-assets")

(defn dynamic-asset-map []
  {"/js/viewer.js" (str "https://storage.clerk.garden/nextjournal/" prefix "@" (front-end-hash) "/viewer.js")})

(defn build+upload-viewer-resources []
  (let [front-end-hash (front-end-hash)]
    (when-not (cas/tag-exists? {:tag front-end-hash})
      (println (format "Could not find entry at %s. Building..." front-end-hash))
      ((requiring-resolve 'babashka.tasks/run) 'build:js)
      (println "Uploading...")
      (let [res (cas/cas-put {:path "build"
                              :auth-token (System/getenv "GITHUB_TOKEN")
                              :namespace "nextjournal"
                              :tag (str prefix "@" front-end-hash)})]
        (println res))
      (println "Done"))))
