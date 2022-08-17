(ns nextjournal.clerk.distributed-cache
  "Distributed Caching for Clerk.

  Currently only supports up & downlading Clerk's cache to Google Cloud Storage using the `gsutil` command."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.eval :as eval]))

(defn doc->hashes [{:as doc :keys [->analysis-info ->hash blocks]}]
  (into []
        (comp (filter (fn [{:keys [var form]}]
                        (let [{:keys [no-cache? freezable? ns-effect?]} (->analysis-info (or var form))]
                          (and freezable? (not no-cache?) (not ns-effect?)))))
              (map (fn [{:keys [var form]}]
                     (->hash (or var form)))))
        blocks))

(defn state->hashes [state]
  (into (sorted-set)
        (mapcat doc->hashes)
        state))

(defn hash->file [hash]
  (eval/->cache-file (str "@" hash)))

#_(hash-files (state->hashes state'))

(defn ->blob-names [hashes]
  (into []
        (comp (map hash->file)
              (filter fs/exists?)
              (map slurp))
        hashes))

(def bucket
  "gs://ductile-clerk/v1/")

(defn gsutil-cp [destination files]
  (let [{:keys [out err exit]} (shell/sh "gsutil" "-m" "cp" "-n" "-I" destination :in (str/join "\n" files))]
    exit))

(defn download! [docs]
  (eval/create-cache-dir)
  (let [hashes (state->hashes docs)
        hash-files-in-bucket (mapv #(str bucket "@" %) hashes)
        _ (gsutil-cp config/cache-dir hash-files-in-bucket)
        blob-files (mapv #(str bucket %) (->blob-names hashes))]
    (gsutil-cp config/cache-dir blob-files)))

(defn upload! [docs]
  (let [docs upload-docs
        exisiting-hashes (filterv (comp fs/exists? hash->file) (state->hashes docs))
        hash-files (mapv hash->file exisiting-hashes)
        blob-files (mapv eval/->cache-file (->blob-names exisiting-hashes))
        cache-files (concat hash-files blob-files)]
    (gsutil-cp bucket cache-files)))


#_(upload! state')

