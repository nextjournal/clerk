(ns nextjournal.clerk.render.hashing
  "Computes a hash based for Clerk's render cljs bundle."
  {:no-doc true}
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [clojure.java.io :as io]
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

(defn resource-path [res]
  (let [r (io/resource res)]
    (when (not= "file" (.getProtocol r))
      (throw (ex-info (format "Expecting '%s' to be a file. Resource hashing cannot be run against Clerk jar." res) {:resource res})))
    (fs/path r)))

(defn get-base-dir [] (-> (resource-path "nextjournal/clerk.clj") fs/parent fs/parent fs/parent))
#_(get-base-dir)

(defn front-end-hash []
  (let [base-dir (get-base-dir)]
    (str (djv/file-set-hash base-dir (file-set base-dir)))))
#_(front-end-hash)

(defn clerk-sources+notebooks-hash []
  (let [base-dir (get-base-dir)]
    (str (djv/file-set-hash base-dir
                            (concat (fs/glob base-dir "notebooks/**.{clj,cljc,md}")
                                    (fs/glob base-dir "src/**.{clj,cljs,cljc}"))))))
#_(clerk-sources+notebooks-hash)

(defn assets-tag []
  (str "clerk-assets@" (front-end-hash)))

(defn stylesheet-tag []
  (str "clerk-compiled-stylesheet@" (clerk-sources+notebooks-hash)))

(defn dynamic-asset-map []
  {"/js/viewer.js" (str "https://storage.clerk.garden/nextjournal/" (assets-tag) "/viewer.js?immutable=true")
   "/css/compiled-viewer.css" (str "https://storage.clerk.garden/nextjournal/" (stylesheet-tag) "/compiled-viewer.css?immutable=true")})

(defn compile-css!
  "Compiles a minimal tailwind css stylesheet using clerk notebooks and source files as inputs."
  [dest-file]
  ;; NOTE: a .cjs extension is safer in case the current npm project is of type module (like Clerk's): in this case all .js files
  ;; are treated as ES modules and this is not the case of our tw config.
  (shell "yarn install")
  (println "Compiling CSS…")
  (time
   (shell "yarn tailwindcss"
          "--input" (str (resource-path "stylesheets/viewer.css"))
          "--config" (str (resource-path "stylesheets/tailwind.config.js"))
          "--output" dest-file
          "--minify")))

#_ (compile-css! "compiled-viewer.css")

(defn build+upload-viewer-resources []
  (let [assets-tag (assets-tag)
        stylesheet-tag (stylesheet-tag)
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

    (when-not (tag-exists? {:namespace "nextjournal" :tag stylesheet-tag})
      (println (format "Could not find entry at %s. Building..." stylesheet-tag))
      (compile-css! "compiled-viewer.css")
      (println "Uploading...")
      (let [res (cas-put {:path "compiled-viewer.css"
                          :auth-token (System/getenv "GITHUB_TOKEN")
                          :namespace "nextjournal"
                          :tag stylesheet-tag})]
        (doseq [[k v] res]
          (println (str k ": " v)))))
    (println "Done")))

#_(build+upload-viewer-resources)
