(ns nextjournal.clerk.config
  (:require [clojure.string :as str]))

(defn cache-dir []
  (or (System/getProperty "clerk.cache_dir")
      ".cache"))

(defn cache-disabled? []
  (when-let [prop (System/getProperty "clerk.disable_cache")]
    (not= "false" prop)))

(defn cache-disabled? []
  (when-let [prop (System/getProperty "clerk.disable_cache")]
    (not= "false" prop)))

(def default-resource-manifest
  {"/js/viewer.js" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VwLM2wq7ZJ8xd2zmqCWVgdmU9xrVYt6YDYryMVeJHbNUDarrbLScR4LKvtsQjNax2h99EYeU8qWnF4ryQSjN1z4Pq"})

(defonce resource-manifest
  (or (when-let [prop (System/getProperty "clerk.resource_manifest")]
        (when-not (str/blank? prop)
          (read-string prop)))
      default-resource-manifest))


(def ^:dynamic *in-clerk* false)

(def ^:dynamic *bounded-count-limit*
  (or (let [limit (System/getProperty "clerk.bounded-count-limit")]
        (try
          (some-> limit not-empty Integer/parseInt)
          (catch Exception _
            (throw (ex-info "Invalid value for property `clerk.bounded-count-limit`, must be integer." {:value limit})))))
      1000000))
