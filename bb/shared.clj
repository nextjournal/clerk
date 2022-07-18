(ns shared
  "Shared code between build.clj and bb tasks"
  (:require
   [babashka.process :as p]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(defn latest-sha []
  (str/trim (:out (p/sh "git rev-parse HEAD"))))

(defn meta-edn []
  (-> (slurp "resources/META-INF/nextjournal/clerk/meta.edn")
      edn/read-string))

(defn rev-count []
  (-> (p/process ["git" "rev-list" "HEAD" "--count"] {:out :string})
      p/check :out str/trim Integer/parseInt))

(defn version []
  (let [{:keys [major minor rev-count]} (:version (meta-edn))]
    (format "%d.%d.%d" major minor rev-count)))
