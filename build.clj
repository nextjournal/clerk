(ns build
  (:require
   [babashka.process :as process]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [nextjournal.cas :as cas]
   [nextjournal.clerk.render.hashing :refer [lookup-url]]
   [shared]))

(def lib 'io.github.nextjournal/clerk)
(def class-dir "target/classes")

(def basis (b/create-basis {:project "deps.edn"}))
(def version (shared/version))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn package-asset-map [_]
  (let [asset-map (slurp lookup-url)]
    (spit "target/classes/clerk-asset-map.edn" asset-map)))

(defn jar [_]
  (b/delete {:path "target"})
  (println "Producing jar:" jar-file)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :scm {:url "http://github.com/nextjournal/clerk"
                      :connection "scm:git:git://github.com/nextjournal/clerk.git"
                      :developerConnection "scm:git:ssh://git@github.com/nextjournal/clerk.git"
                      :tag (str "v" version)}
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir
               :replace {}})
  (package-asset-map {})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install [_]
  (jar {})
  (println "Installing jar:" jar-file)
  (b/install {:basis basis
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir}))

(defn deploy [opts]
  (jar opts)
  (try ((requiring-resolve 'deps-deploy.deps-deploy/deploy)
        (merge {:installer :remote
                :artifact jar-file
                :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
               opts))
       (catch Exception e
         (if-not (str/includes? (ex-message e) "redeploying non-snapshots is not allowed")
           (throw e)
           (println "This release was already deployed."))))
  opts)

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
