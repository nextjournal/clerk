(ns build
  (:require
   [babashka.fs :as fs]
   [babashka.process :as process]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as dd]
   [nextjournal.cas :as cas]
   [nextjournal.clerk.config :refer [lookup-url]]))

(def lib 'io.github.nextjournal/clerk)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def version (-> (slurp "resources/META-INF/nextjournal/clerk/meta.edn") edn/read-string :version))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn inline-asset-map [_]
  (let [asset-map (slurp lookup-url)
        config-file (fs/file class-dir "nextjournal/clerk/config.clj")
        config-code (slurp config-file)
        config-code (str/replace config-code #"(?s);; BEGIN_INLINE_ASSET_MAP.*;; END_INLINE_ASSET_MAP" asset-map)]
    (spit config-file config-code)))

(defn jar [_]
  (b/delete {:path "target"})
  (println "Producing jar:" jar-file)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :scm {:url "http://github.com/nextjournal/clerk"
                      :connection "scm:git:git://github.com/nextjournal/clerk.git"
                      :developerConnection "scm:git:ssh://git@github.com/nextjournal/clerk.git"}
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir
               :replace {}})
  (inline-asset-map {})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy [opts]
  (println "Deploying version" jar-file "to Clojars.")
  (format "target/%s-%s.jar" (name lib) version)
  (dd/deploy (merge {:installer :remote
                     :artifact jar-file
                     :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
                    opts)))

(defn asserting [val msg] (assert (seq val) msg) val)

(defn upload! [opts file] (:url (cas/upload! opts file)))

(defn update-resource! [opts _resource _ file]
  (upload! opts file))

(defn get-gsutil [] (str/trim (:out (process/sh ["which" "gsutil"]))))

(def resource->path
  '{viewer.js "/js/viewer.js"})

(defn upload-to-cas [{:keys [resource]}]
  (if-let [target (resource->path resource)]
    (update-resource! {:exec-path (str/trim (asserting (get-gsutil) "Can't find gsutil executable."))
                       :target-path "gs://nextjournal-cas-eu/data/"} target :uploading (str "build/" resource))
    (throw (ex-info (str "unsupported resource " resource) {:supported-resources (keys resource->path)}))))
