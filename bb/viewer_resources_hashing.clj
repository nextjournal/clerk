(ns viewer-resources-hashing
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [nextjournal.dejavu :as djv]))

(def output-dirs ["resources/public/ui"
                  "resources/public/build"])

;; Example link in bucket:
;; "https://storage.googleapis.com/nextjournal-cas-eu/data/8VwKauX6JACEP3K6ahNmP5p1w7rWdhKzeGXCDrHMnJiVrUxHVxcm3Xj84K2r3fcAKWxMQKzqoFe92osgFEHCuKCtZC"

(def gs-bucket "gs://nextjournal-cas-eu")
(def storage-base-url "https://storage.googleapis.com/nextjournal-cas-eu")

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
