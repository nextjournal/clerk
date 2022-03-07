(ns hashing
  (:require [babashka.classpath :as cp]
            [babashka.fs :as fs]
            [babashka.tasks :as tasks :refer [shell]]
            [clojure.string :as str]
            [nextjournal.dejavu :as djv]))

(def output-dirs ["resources/public/ui"
                  "resources/public/build"])

;; Example link in bucket:
;; "https://storage.googleapis.com/nextjournal-cas-eu/data/8VwKauX6JACEP3K6ahNmP5p1w7rWdhKzeGXCDrHMnJiVrUxHVxcm3Xj84K2r3fcAKWxMQKzqoFe92osgFEHCuKCtZC"

(def gs-bucket "gs://nextjournal-cas-eu/data")
(def base-url "https://storage.googleapis.com/nextjournal-cas-eu/data")

(defn sha512s []
  (let [files (map str (mapcat #(fs/glob % "**.{js,css}") output-dirs))
        sha512 (map (comp djv/sha512 slurp) files)]
    (zipmap files sha512)))

(defn resource [f]
  (fs/file "resources/public" (str/replace f #"^/" "")))

(defn classpath-dirs []
  (tasks/run 'yarn-install);
  (->> (shell {:out :string} "yarn --silent shadow-cljs classpath")
       :out
       str/trim
       str/split-lines
       first
       cp/split-classpath
       (remove (comp not fs/exists?))
       (take-while fs/directory?)
       (remove #(str/includes? % "test"))))

(defn file-set []
  (reduce into []
          [["deps.edn"
            "shadow-cljs.edn"
            "yarn.lock"]
           (djv/cljs-files (classpath-dirs))]))

(defn write-hash []
  (let [front-end-hash (str (djv/file-set-hash (file-set)))]
    (spit "resources/front-end-hash.txt" front-end-hash)))

(def gs-url-prefix "https://storage.googleapis.com/nextjournal-cas-eu/data")

(defn lookup-url [lookup-hash]
  (str gs-bucket "/lookup/" lookup-hash))

(defn cas-link [hash]
  (str gs-url-prefix "/" hash))

(defn refresh-cdn []
  (let [front-end-hash (str/trim (slurp "resources/front-end-hash.txt"))
        manifest (str (fs/create-temp-file))
        res (djv/gs-copy (str (lookup-url front-end-hash)) manifest false)]
    (when (= res ::djv/not-found)
      (tasks/run 'build:js)
      (let [content-hash (djv/sha512 (slurp "build/viewer.js"))
            viewer-js-http-link (str (cas-link content-hash) "?cache=false")]
        (spit manifest {"/js/viewer.js" viewer-js-http-link})
        (println "Manifest:" (slurp manifest))
        (println "Coping manifest to" (lookup-url front-end-hash))
        (djv/gs-copy manifest (lookup-url front-end-hash))
        (djv/gs-copy "build/viewer.js" (str gs-bucket "/" content-hash))))))
