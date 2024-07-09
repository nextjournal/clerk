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

(def resource-manifest-from-props
  (when-let [prop (System/getProperty "clerk.resource_manifest")]
    (when-not (str/blank? prop)
      (read-string prop))))

(def !asset-map
  ;; In mvn releases, the asset map is available in the artifact
  (delay (or (some-> (io/resource "clerk-asset-map.edn") slurp edn/read-string)
             (try ((requiring-resolve 'nextjournal.clerk.render.hashing/dynamic-asset-map))
                  (catch Exception e
                    (throw (ex-info "Error reading dynamic asset map"
                                    (or (ex-data e)
                                        {}) e)))))))

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


(def render-repl-config
  (delay (when-let [config (let [prop (System/getProperty "clerk.render_repl")]
                             (when-not (str/blank? prop)
                               (merge {:nrepl-port 1339
                                       :websocket-port 1340}
                                      (edn/read-string prop))))]
           (let [start! (requiring-resolve 'sci.nrepl.browser-server/start!)]
             (doto config
               start!)))))
