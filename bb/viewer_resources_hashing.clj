(ns viewer-resources-hashing
  (:require [alphabase.base58 :refer [encode] :rename {encode base-58}]
            [babashka.fs :as fs]
            [clojure.string :as str])
  (:import (java.security MessageDigest)))

(def output-dirs ["resources/public/ui"
                  "resources/public/build"])

;; Example link in bucket:
;; "https://storage.googleapis.com/nextjournal-cas-eu/data/8VwKauX6JACEP3K6ahNmP5p1w7rWdhKzeGXCDrHMnJiVrUxHVxcm3Xj84K2r3fcAKWxMQKzqoFe92osgFEHCuKCtZC"

(def gs-bucket "gs://nextjournal-cas-eu")
(def storage-base-url "https://storage.googleapis.com/nextjournal-cas-eu")

(defn sha [s algo]
  (let [instance (MessageDigest/getInstance algo)
        bytes (.digest instance (cond (string? s)
                                      (.getBytes s)
                                      (fs/exists? s)
                                      (fs/read-all-bytes s)
                                      :else (throw (IllegalArgumentException. (str (type s))))))
        string (base-58 bytes)]
    string))

(defn sha1 [s]
  (sha s "SHA-1"))

(defn sha512 [s]
  (sha s "SHA-512"))

(defn sha1-file [f]
  (let [fn (str/replace f fs/file-separator "|")
        fn (str fn ".sha1")]
    fn))

(defn file-set-hash
  "Returns combined sha1 of file-set contents."
  [file-set]
  (let [out-dir (fs/file ".work/.fileset_hash")
        _ (fs/create-dirs out-dir)
        out-file (fs/file out-dir "aggregate.txt")]
    (spit out-file "")
    (doseq [f (sort file-set)]
      (let [sf (sha1-file f)]
        (spit out-file (str sf ":" (sha1 (slurp f)) "\n") :append true)))
    (println "Aggregate sha-1 hash:")
    (println (slurp out-file))
    (println "SHA-1:" (sha1 (slurp out-file)))
    (sha1 (slurp out-file))))

(defn sha512s []
  (let [files (map str (mapcat #(fs/glob % "**.{js,css}") output-dirs))
        sha512 (map (comp djv/sha512 slurp) files)]
    (zipmap files sha512)))

(defn resource [f]
  (fs/file "resources/public" (str/replace f #"^/" "")))

(defn file-set []
  (reduce into []
          [["deps.edn"
            "render/deps.edn"
            "shadow-cljs.edn"
            "yarn.lock"]
           (djv/cljs-files ["src" "resources"] #_(classpath-dirs))]))

(defn front-end-hash []
  (str (djv/file-set-hash (file-set))))

(defn lookup-url [lookup-hash]
  (str gs-bucket "/lookup/" lookup-hash))

(defn asset-name [hash suffix]
  (str "/assets/" hash
       (when suffix
         (str "-" suffix))))

(defn build+upload-viewer-resources []
  (let [front-end-hash (front-end-hash)
        manifest (str (fs/create-temp-file))
        res (djv/gs-copy (str (lookup-url front-end-hash)) manifest false)]
    (when (= res ::djv/not-found)
      ((requiring-resolve 'babashka.tasks/run) 'build:js)
      (let [content-hash (djv/sha512 (slurp "build/viewer.js"))
            viewer-js-http-link (str storage-base-url (asset-name content-hash "viewer.js"))]
        (spit manifest {"/js/viewer.js" viewer-js-http-link})
        (println "Manifest:" (slurp manifest))
        (println "Coping manifest to" (lookup-url front-end-hash))
        (djv/gs-copy manifest (lookup-url front-end-hash))
        (djv/gs-copy "build/viewer.js" (str gs-bucket (asset-name content-hash "viewer.js")))))))
