(ns build
  (:require
   [babashka.process :as process]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [nextjournal.cas :as cas]
   [nextjournal.clerk.render.hashing]
   [shared]))

(def lib 'io.github.nextjournal/clerk)
(def class-dir "target/classes")

(def basis (-> (b/create-basis {:project "deps.edn"})
               (update :libs dissoc 'io.github.nextjournal/dejavu)))

(def version (shared/version))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn package-clerk-asset-map [{:as opts :keys [target-dir]}]
  (when-not target-dir
    (throw (ex-info "target dir must be set" {:opts opts})))
  (let [asset-map (slurp (nextjournal.clerk.render.hashing/get-lookup-url))]
    (spit (str target-dir java.io.File/separator "clerk-asset-map.edn") asset-map)))

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
  (package-clerk-asset-map {:target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn uberjar [_]
  (b/delete {:path "target"})
  (let [basis (-> (b/create-basis {:project "deps.edn" :aliases [:cli]})
                  (update :libs dissoc 'io.github.nextjournal/dejavu))]
    (println "Producing uberjar:" jar-file)
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
    (package-clerk-asset-map {:target-dir class-dir})
    (println "Compiling sources...")
    (b/compile-clj {:basis basis
                    :src-dirs ["src"]
                    :class-dir class-dir
                    :ns-compile '[nextjournal.clerk
                                  babashka.cli]}))
  (println "Packaging uberjar...")
  (b/uber {:class-dir class-dir
           :basis basis
           :uber-file jar-file
           :main 'nextjournal.clerk}))

(defn native-image [opts]
  #_(uberjar opts)
  (process/shell {:inherit true}
                 "native-image --diagnostics-mode --initialize-at-build-time -H:Name=./target/clerk -H:+ReportExceptionStackTraces --language:js -jar"
                 jar-file))

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
