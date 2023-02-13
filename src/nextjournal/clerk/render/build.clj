(ns nextjournal.clerk.render.build
  (:require [nextjournal.clerk.render.hashing :refer [assets-tag]]
            [nextjournal.cas-client.api :as cas]))

(defn build+upload-viewer-resources []
  (let [tag (assets-tag)]
    (when-not (cas/tag-exists? {:namespace "nextjournal"
                                :tag tag})
      (println (format "Could not find entry at %s. Building..." tag))
      ((requiring-resolve 'babashka.tasks/run) 'build:js)
      (println "Uploading...")
      (let [res (cas/cas-put {:path "build"
                              :auth-token (System/getenv "GITHUB_TOKEN")
                              :namespace "nextjournal"
                              :tag tag})]
        (doseq [[k v] res]
          (println (str k ": " v))))
      (println "Done"))))
