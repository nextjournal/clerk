(ns nextjournal.clerk.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))


(def cache-dir
  (or (System/getProperty "clerk.cache_dir")
      ".clerk/cache"))

(def cache-disabled?
  (when-let [prop (System/getProperty "clerk.disable_cache")]
    (not= "false" prop)))

(def resource-manifest-from-props
  (when-let [prop (System/getProperty "clerk.resource_manifest")]
    (when-not (str/blank? prop)
      (read-string prop))))

(defn try-slurp [s]
  (try (slurp s)
       (catch Exception _ nil)))

(defn read-dynamic-asset-map!
  "Computes a hash for Clerk's cljs bundle and tries to load the asset manifest for it.

  Used only when Clerk is used as a git dep, should never be called from the jar."
  []
  (if-let [front-end-hash (try (requiring-resolve 'viewer-resources-hashing/front-end-hash)
                               (catch Exception _ nil))]
    (edn/read-string (slurp (str "https://storage.googleapis.com/nextjournal-cas-eu" "/lookup/" (front-end-hash))))
    (throw (ex-info "Error reading dynamic asset map" {}))))

(def !asset-map
  ;; In mvn releases, the asset map is available in the artifact
  (delay (or (some-> (io/resource "clerk-asset-map.edn") slurp edn/read-string)
             (read-dynamic-asset-map!))))

(defonce !resource->url
  ;; contains asset manifest in the form:
  ;; {"/js/viewer.js" "https://..."}
  (atom (or resource-manifest-from-props
            @!asset-map)))

#_(swap! !resource->url assoc "/css/viewer.css" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VvAV62HzsvhcsXEkHP33uj4cV9UvdDz7DU9qLeVRCfEP9kWLFAzaMKL77trdx898DzcVyDVejdfxvxj5XB84UpWvQ")
#_(swap! !resource->url dissoc "/css/viewer.css")
#_(reset! !resource->url identity)
#_(reset! !resource->url default-resource-manifest)
#_(reset! !resource->url (-> (slurp lookup-url) edn/read-string))


(def ^:dynamic *in-clerk* false)

(def ^:dynamic *bounded-count-limit*
  (or (let [limit (System/getProperty "clerk.bounded-count-limit")]
        (try
          (some-> limit not-empty Integer/parseInt)
          (catch Exception _
            (throw (ex-info "Invalid value for property `clerk.bounded-count-limit`, must be integer." {:value limit})))))
      1000000))
