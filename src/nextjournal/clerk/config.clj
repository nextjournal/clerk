(ns nextjournal.clerk.config
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def cache-dir
  (or (System/getProperty "clerk.cache_dir")
      ".clerk/.cache"))

(def cache-disabled?
  (when-let [prop (System/getProperty "clerk.disable_cache")]
    (not= "false" prop)))

(def gs-url-prefix "https://storage.googleapis.com/nextjournal-cas-eu")
(def lookup-hash (str/trim (slurp (io/resource "viewer-js-hash"))))
(def lookup-url (str gs-url-prefix "/lookup/" lookup-hash))
(def local-lookup (str (io/file cache-dir "lookup" lookup-hash)))

(def resource-manifest-from-props
  (when-let [prop (System/getProperty "clerk.resource_manifest")]
    (when-not (str/blank? prop)
      (read-string prop))))

(def cached-lookup
  (delay
    (edn/read-string
     (if (fs/exists? local-lookup)
       (slurp local-lookup)
       (let [lookup (slurp lookup-url)]
         (prn (fs/create-dirs (fs/parent local-lookup)))
         (spit local-lookup lookup)
         lookup)))))

(get cached-lookup :a)

(defonce !resource->url
  ;; contains asset manifest in the form:
  ;; {"/js/viewer.js" "https://..."}
  (atom (or resource-manifest-from-props
            cached-lookup)))

(defn resource->url [resource]
  (or (get @!resource->url resource)
      (get @cached-lookup resource)))

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
