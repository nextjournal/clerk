(ns build
  (:require
   [babashka.process :as process]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [nextjournal.clerk.config :as config]
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
  (let [asset-map @config/!asset-map]
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
