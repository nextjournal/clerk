(ns viewer-resources-hashing
  (:require
   [babashka.classpath :as cp]
   [babashka.curl :as curl]
   [babashka.fs :as fs]
   [babashka.tasks :as tasks :refer [shell]]
   [clojure.string :as str]
   [nextjournal.dejavu :as djv]))

(def output-dirs ["resources/public/ui"
                  "resources/public/build"])

;; Example link in bucket:
;; "https://storage.googleapis.com/nextjournal-cas-eu/data/8VwKauX6JACEP3K6ahNmP5p1w7rWdhKzeGXCDrHMnJiVrUxHVxcm3Xj84K2r3fcAKWxMQKzqoFe92osgFEHCuKCtZC"

(def gs-bucket "gs://nextjournal-cas-eu")
(def base-url "https://storage.googleapis.com/nextjournal-cas-eu")

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

(def viewer-js-hash-file "resources/viewer-js-hash")

(defn write-viewer-resource-hash
  []
  (let [front-end-hash (str (djv/file-set-hash (file-set)))]
    (spit viewer-js-hash-file front-end-hash)))

(defn lookup-url [lookup-hash]
  (str gs-bucket "/lookup/" lookup-hash))

(defn cas-link [hash]
  (str base-url "/data/" hash))

(defn build+upload-viewer-resources []
  (let [front-end-hash (str/trim (slurp viewer-js-hash-file))
        manifest (str (fs/create-temp-file))
        res (djv/gs-copy (str (lookup-url front-end-hash)) manifest false)]
    (when (= res ::djv/not-found)
      (tasks/run 'build:js)
      (let [content-hash (djv/sha512 (slurp "build/viewer.js"))
            viewer-js-http-link (str (cas-link content-hash))]
        (spit manifest {"/js/viewer.js" viewer-js-http-link})
        (println "Manifest:" (slurp manifest))
        (println "Coping manifest to" (lookup-url front-end-hash))
        (djv/gs-copy manifest (lookup-url front-end-hash))
        (djv/gs-copy "build/viewer.js" (str gs-bucket "/data/" content-hash))))))

(defn sha512-ize [file]
  (let [hash (djv/sha512 (slurp file))]
    hash))

(def font-css-link "https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;700&family=Fira+Mono:wght@400;700&family=Fira+Sans+Condensed:ital,wght@0,700;1,700&family=Fira+Sans:ital,wght@0,400;0,500;0,700;1,400;1,500;1,700&family=PT+Serif:ital,wght@0,400;0,700;1,400;1,700&display=swap")

(defn extract-font-links [css]
  (let [urls (map second (re-seq #"url\((.*?)\)" css))]
    urls))

(defn store-asset
  ([a] (store-asset a (:body (curl/get a))))
  ([a content]
   (let [f (if (str/starts-with? a "http")
             (let [b content
                   f (fs/file (fs/create-temp-file))]
               (spit f b)
               f)
             a)
         hash (sha512-ize f)
         gs-dest (str gs-bucket "/data/" hash)
         gs-url (cas-link hash)]
     (println "Copying" a "to" gs-dest)
     #_(djv/gs-copy f gs-dest)
     [a gs-url])))

(defn hash-assets []
  (let [font-css (slurp font-css-link)
        font-links (extract-font-links font-css)
        assets (into ["https://cdn.tailwindcss.com/3.0.23?plugins=typography@0.5.2"
                      "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css"]
                     font-links)
        manifest (into {} (for [a assets]
                            (store-asset a)))
        font-css (reduce (fn [acc l]
                           (str/replace acc l (let [remote (get manifest l)
                                                    hash (last (str/split remote #"/"))
                                                    cached (str/replace remote
                                                                        (str base-url "/data")
                                                                        "/cached")]
                                                (spit (str ".clerk/.cache/" hash)
                                                      (:body (curl/get remote)))
                                                cached))) font-css font-links)
        [font-css-link gurl] (store-asset font-css-link font-css)
        manifest (assoc manifest font-css-link gurl)
        manifest (reduce (fn [acc l]
                           (let [remote (get manifest l)
                                 hash (last (str/split remote #"/"))]
                             (assoc acc (str "/cached/" hash) remote))) manifest font-links)]
    ((requiring-resolve 'clojure.pprint/pprint) manifest)
    (spit "resources/asset_manifest.edn"
          {:asset-map manifest})))
