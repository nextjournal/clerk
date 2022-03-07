(ns nextjournal.clerk.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def cache-dir
  (or (System/getProperty "clerk.cache_dir")
      ".clerk/cache"))

(def cache-disabled?
  (when-let [prop (System/getProperty "clerk.disable_cache")]
    (not= "false" prop)))

(def gs-url-prefix "https://storage.googleapis.com/nextjournal-cas-eu/data")
(def lookup-hash (str/trim (slurp (io/resource "front-end-hash.txt"))))
(def lookup-url (str gs-url-prefix "/lookup/" lookup-hash))

(def resource-manifest-from-props
  (when-let [prop (System/getProperty "clerk.resource_manifest")]
    (when-not (str/blank? prop)
      (read-string prop))))

(prn lookup-url)

(defonce !resource->url
  (atom (or resource-manifest-from-props
            ;; assume that CI will have published a CAS-link under this lookup,
            ;; prior to hitting this code-path
            (edn/read-string (slurp lookup-url)))))

(prn @!resource->url)

#_(swap! !resource->url assoc "/css/viewer.css" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VvAV62HzsvhcsXEkHP33uj4cV9UvdDz7DU9qLeVRCfEP9kWLFAzaMKL77trdx898DzcVyDVejdfxvxj5XB84UpWvQ")
#_(swap! !resource->url dissoc "/css/viewer.css")
#_(reset! !resource->url identity)
#_(reset! !resource->url default-resource-manifest)


(def ^:dynamic *in-clerk* false)

(def ^:dynamic *bounded-count-limit*
  (or (let [limit (System/getProperty "clerk.bounded-count-limit")]
        (try
          (some-> limit not-empty Integer/parseInt)
          (catch Exception _
            (throw (ex-info "Invalid value for property `clerk.bounded-count-limit`, must be integer." {:value limit})))))
      1000000))
