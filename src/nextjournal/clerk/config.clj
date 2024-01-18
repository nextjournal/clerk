(ns nextjournal.clerk.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defonce ^:private config (atom {}))

(defn load! [c]
  (reset! config c))

(defn- kebab->snake-case [s]
  (str/replace s "-" "_"))

(defn- path->property-name [path]
  (str "clerk." (str/join "." (map (comp kebab->snake-case name) path))))

(defn- path->env-name [path]
  (str "CLERK_" (str/join "__" (map (comp kebab->snake-case name) path))))

(defn get-config
  ([path]
   (get-config path nil))
  ([path default]
   (get-config path default {}))
  ([path default {:keys [valid? parse]
                  :or {valid? (constantly true)
                       parse identity}}]
   (let [val (let [x (get-in @config path ::not-found)]
               (if (not= ::not-found x)
                 x
                 (or (System/getProperty (path->property-name path))
                     (System/getenv (path->env-name path)))))]
     (try
       (let [parsed-val (parse val)]
         (if (valid? parsed-val)
           parsed-val
           (do
             (println (format "WARN: Invalid value %s for %s. Using default %s." parsed-val path default))
             default)))
       (catch Exception _
         (println (format "WARN: Failed parsing value %s for %s. Using default %s." val path default))
         default)))))

(defn cache-dir [] (get-config [:cache-dir] ".clerk/cache"))

(defn cache-disabled? [] (get-config [:disable-cache] false))

(def resource-manifest-from-props (get-config [:resource-manifest] nil {:parse read-string}))

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
  (get-config [:bounded-count-limit]
              1000000
              {:parse parse-long
               :valid? int?}))
