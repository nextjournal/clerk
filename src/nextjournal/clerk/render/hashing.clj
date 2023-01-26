(ns nextjournal.clerk.render.hashing
  "Computes a hash based for Clerk's render cljs bundle."
  {:no-doc true}
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.dejavu :as djv]
            [nextjournal.cas-proxy.tags.http :as cas-tags]
            [nextjournal.cas-proxy.cas.http :as cas]))

(def output-dirs ["resources/public/ui"
                  "resources/public/build"])

;; Example link in bucket:
;; "https://storage.googleapis.com/nextjournal-cas-eu/data/8VwKauX6JACEP3K6ahNmP5p1w7rWdhKzeGXCDrHMnJiVrUxHVxcm3Xj84K2r3fcAKWxMQKzqoFe92osgFEHCuKCtZC"

(def gs-bucket "gs://nextjournal-cas-eu")
(def storage-base-url "https://storage.googleapis.com/nextjournal-cas-eu")

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

(defn bucket-lookup-url [lookup-hash]
  (str gs-bucket "/lookup/" lookup-hash))

(defn asset-name [hash suffix]
  (str "/assets/" hash
       (when suffix
         (str "-" suffix))))

(defn get-lookup-url []
  (str storage-base-url "/lookup/" (front-end-hash)))

#_(get-lookup-url)

(defn read-dynamic-asset-map!
  "Computes a hash for Clerk's cljs bundle and tries to load the asset manifest for it.

  Used only when Clerk is used as a git dep, should never be called from the jar."
  []
  {"/js/viewer.js" (cas-tags/tag-url {:namespace "staging.clerk.garden"
                                      :tag (front-end-hash)
                                      :path "viewer.js"})})

#_(read-dynamic-asset-map!)

(defn build+upload-viewer-resources [& args]
  (let [front-end-hash (front-end-hash)
        auth-token (System/getenv "CLERK_CAS_AUTH_TOKEN")]
    (assert (some? auth-token) "Please set CLERK_CAS_AUTH_TOKEN")
    (let [{:keys [status]} (cas-tags/tag-get {:namespace "staging.clerk.garden" :tag front-end-hash})]
      (if (or (= 404 status) (some #{"-f" "--force"} args))
        (do (println "Did not find viewer in CAS. Compiling…")
            ((requiring-resolve 'babashka.tasks/run) 'build:js)
            (println "Uploading…")
            (let [manifest-path (get (cas/cas-put "build") "manifest-path")]
              (println "Manifest path:" manifest-path)
              (println (if (= 200 (:status (cas-tags/tag-put {:auth-token auth-token :namespace "staging.clerk.garden" :tag front-end-hash :target manifest-path})))
                         "Updated tag successfully."
                         "Failed to update tag."))))
        (println "Current version already uploaded. Use -f to force rebuild and reupload.")))))
