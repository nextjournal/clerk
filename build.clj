(ns build
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]
            [nextjournal.cas :as cas]
            [rewrite-clj.zip :as z]))

(def lib 'io.github.nextjournal/clerk)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def version (-> (slurp "resources/META-INF/nextjournal/clerk/meta.edn") edn/read-string :version))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

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
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
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

(def clerk-view-file-path  "src/nextjournal/clerk/view.clj")

(defn replace-resource [{:keys [clerk-view-file]} resource url]
  (-> clerk-view-file
      io/file
      z/of-file
      (z/find-token z/next #(and (= 'def (z/sexpr %))
                                 (= 'resource->static-url (-> % z/right z/sexpr))))
      (z/find-value z/next resource)
      z/right
      (z/edit (fn [old-url]
                (println "Replacing resource: " resource "\n"
                         (if (= old-url url)
                           (str "url " old-url " is up-to-date.")
                           (str "updated from " old-url " to " url ".")))
                url))
      z/root-string))

(defn upload! [opts file] (:url (cas/upload! opts file)))

(defn update-resource! [{:as opts :keys [clerk-view-file]} resource _ file]
  (spit clerk-view-file
        (replace-resource opts
                          resource
                          (upload! opts file))))

(defn get-gsutil [] (str/trim (:out (process/sh ["which" "gsutil"]))))

(def resource->path
  '{viewer.js "/js/viewer.js"
    app.css "/css/app.css"})

(defn upload-to-cas+rewrite-sha [{:keys [resource]}]
  (if-let [target (resource->path resource)]
    (update-resource! {:clerk-view-file (io/file clerk-view-file-path)
                       :exec-path (str/trim (asserting (get-gsutil) "Can't find gsutil executable."))
                       :target-path "gs://nextjournal-cas-eu/data/"} target :uploading (str "build/" resource))
    (throw (ex-info (str "unsupported resource " resource) {:supported-resources (keys resource->path)}))))
