(ns nextjournal.clerk.config)

(defn cache-dir []
  (or (System/getProperty "clerk.cache_dir")
      ".cache"))

(defn cache-disabled? []
  (when-let [prop (System/getProperty "clerk.disable_cache")]
    (not= "false" prop)))

(def ^:dynamic *in-clerk* false)

(def ^:dynamic *bounded-count-limit*
  (or (let [limit (System/getProperty "clerk.bounded-count-limit")]
        (try
          (some-> limit not-empty Integer/parseInt)
          (catch Exception _
            (throw (ex-info "Invalid value for property `clerk.bounded-count-limit`, must be integer." {:value limit})))))
      1000000))
