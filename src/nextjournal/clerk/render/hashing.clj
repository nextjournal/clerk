(ns nextjournal.clerk.render.hashing
  "Computes a hash based for Clerk's render cljs bundle."
  {:no-doc true}
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.dejavu :as djv]))

(def output-dirs ["resources/public/ui"
                  "resources/public/build"])

;; Example link in bucket:
;; "https://storage.googleapis.com/nextjournal-cas-eu/data/8VwKauX6JACEP3K6ahNmP5p1w7rWdhKzeGXCDrHMnJiVrUxHVxcm3Xj84K2r3fcAKWxMQKzqoFe92osgFEHCuKCtZC"

(def gs-bucket "gs://nextjournal-cas-eu")
(def storage-base-url "https://storage.googleapis.com/nextjournal-cas-eu")

(def base-dir
  (-> (fs/file (io/resource "nextjournal/clerk.clj")) fs/parent fs/parent fs/parent))

(defn file-set []
  (reduce into
          []
          [(mapv #(fs/file base-dir %) ["deps.edn"
                                        "render/deps.edn"
                                        "shadow-cljs.edn"
                                        "yarn.lock"])
           (djv/cljs-files (mapv #(fs/file base-dir %) ["src" "resources"]))]))

#_(System/setProperty "nextjournal.dejavu.debug" "1")

(defn front-end-hash []
  (str (djv/file-set-hash base-dir (file-set))))

(defn bucket-lookup-url [lookup-hash]
  (str gs-bucket "/lookup/" lookup-hash))

(def lookup-url
  (str storage-base-url "/lookup/" (front-end-hash)))

(defn asset-name [hash suffix]
  (str "/assets/" hash
       (when suffix
         (str "-" suffix))))

(defn read-dynamic-asset-map!
  "Computes a hash for Clerk's cljs bundle and tries to load the asset manifest for it.

  Used only when Clerk is used as a git dep, should never be called from the jar."
  []
  (edn/read-string (try
                     (slurp lookup-url)
                     (catch java.io.FileNotFoundException e
                       (throw (ex-info (str "Clerk could not find dynamic asset map at " lookup-url) {:url lookup-url} e))))))

#_(read-dynamic-asset-map!)

(defn build+upload-viewer-resources []
  (let [front-end-hash (front-end-hash)
        manifest (str (fs/create-temp-file))
        res (djv/gs-copy (str (bucket-lookup-url front-end-hash)) manifest false)]
    (when (= res ::djv/not-found)
      ((requiring-resolve 'babashka.tasks/run) 'build:js)
      (let [content-hash (djv/sha512 (slurp "build/viewer.js"))
            viewer-js-http-link (str storage-base-url (asset-name content-hash "viewer.js"))]
        (spit manifest {"/js/viewer.js" viewer-js-http-link})
        (println "Manifest:" (slurp manifest))
        (println "Coping manifest to" (bucket-lookup-url front-end-hash))
        (djv/gs-copy manifest (bucket-lookup-url front-end-hash))
        (djv/gs-copy "build/viewer.js" (str gs-bucket (asset-name content-hash "viewer.js")))))))
