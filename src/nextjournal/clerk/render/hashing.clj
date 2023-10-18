(ns nextjournal.clerk.render.hashing
  "Computes a hash based for Clerk's render cljs bundle."
  {:no-doc true}
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.dejavu :as djv]))

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

(defn get-base-dir []
  (let [resource (io/resource "nextjournal/clerk.clj")]
    (doto (when (= "file" (.getProtocol resource))
            (-> (fs/file resource) fs/parent fs/parent fs/parent))
      (assert "Clerk cannot infer base folder for computing assets hashes."))))

(defn front-end-hash []
  (let [base-dir (get-base-dir)]
    (str (djv/file-set-hash base-dir (file-set base-dir)))))

(defn clojure-sources-hash []
  (let [base-dir (get-base-dir)]
    (str (djv/file-set-hash base-dir (fs/glob base-dir "**/*.{clj,cljs,cljc}")))))

(defn assets-tag []
  (str "clerk-assets@" (front-end-hash)))

(defn tw-candidate-classes-tag []
  (str "clerk-tw-candidate-classes@" (clojure-sources-hash)))

(defn dynamic-asset-map []
  {"/js/viewer.js" (str "https://storage.clerk.garden/nextjournal/" (assets-tag) "/viewer.js?immutable=true")
   "tw-candidate-classes.txt" (str "https://storage.clerk.garden/nextjournal/" (tw-candidate-classes-tag) "/tw-candidate-classes.txt?immutable=true")})

(defn collect-tw-candidate-classes [dest-file]
  (shell {:dir "tw_extractor"} "yarn install")
  (shell {:dir "tw_extractor"} "yarn nbb -m tailwind-extractor" dest-file))

#_ (collect-tw-candidate-classes "test.tw")
#_ (shell {:out :string} "wc -l test.tw")

(defn build+upload-viewer-resources []
  (let [assets-tag (assets-tag)
        tw-classes-tag (tw-candidate-classes-tag)
        tag-exists? (requiring-resolve 'nextjournal.cas-client/tag-exists?)
        cas-put (requiring-resolve 'nextjournal.cas-client/cas-put)]

    (when-not (tag-exists? {:namespace "nextjournal" :tag assets-tag})
      (println (format "Could not find entry at %s. Building..." assets-tag))
      ((requiring-resolve 'babashka.tasks/run) 'build:js)
      (println "Uploading viewer resources...")
      (let [res (cas-put {:path "build"
                          :auth-token (System/getenv "GITHUB_TOKEN")
                          :namespace "nextjournal"
                          :tag assets-tag})]
        (doseq [[k v] res]
          (println (str k ": " v)))))

    (when-not (tag-exists? {:namespace "nextjournal" :tag tw-classes-tag})
      (println (format "Could not find entry at %s. Building..." tw-classes-tag))
      (collect-tw-candidate-classes "tw-candidate-classes.txt")
      (println "Uploading...")
      (let [res (cas-put {:path "tw-candidate-classes.txt"
                          :auth-token (System/getenv "GITHUB_TOKEN")
                          :namespace "nextjournal"
                          :tag tw-classes-tag})]
        (doseq [[k v] res]
          (println (str k ": " v)))))
    (println "Done")))

#_(build+upload-viewer-resources)
