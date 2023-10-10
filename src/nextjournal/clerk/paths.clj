(ns nextjournal.clerk.paths
  "Clerk's paths expansion and paths-fn handling."
  (:require [babashka.fs :as fs])
  (:import [java.net URL]))

(defn ^:private ensure-not-empty [build-opts {:as opts :keys [error expanded-paths]}]
  (if error
    opts
    (if (empty? expanded-paths)
      (merge {:error "nothing to build" :expanded-paths expanded-paths} (select-keys build-opts [:paths :paths-fn :index]))
      opts)))

(defn ^:private maybe-add-index [{:as build-opts :keys [index]} {:as opts :keys [expanded-paths]}]
  (if-not (contains? build-opts :index)
    opts
    (if (and (not (instance? URL index))
             (not (symbol? index))
             (or (not (string? index)) (not (fs/exists? index))))
      {:error "`:index` must be either an instance of java.net.URL or a string and point to an existing file"
       :index index}
      (cond-> opts
        (and index (not (contains? (set expanded-paths) index)))
        (update :expanded-paths conj index)))))

#_(maybe-add-index {:index "book.clj"} {:expanded-paths ["README.md"]})
#_(maybe-add-index {:index 'book.clj} {:expanded-paths ["README.md"]})

(defn resolve-paths [{:as build-opts :keys [paths paths-fn index]}]
  (when (and paths paths-fn)
    (binding [*out* *err*]
      (println "[info] both `:paths` and `:paths-fn` are set, `:paths` will take precendence.")))
  (if (not (or paths paths-fn index))
    {:error "must set either `:paths`, `:paths-fn` or `:index`."
     :build-opts build-opts}
    (cond paths (if (sequential? paths)
                  {:resolved-paths paths}
                  {:error "`:paths` must be sequential" :paths paths})
          paths-fn (let [ex-msg "`:path-fn` must be a qualified symbol pointing at an existing var."]
                     (if-not (qualified-symbol? paths-fn)
                       {:error ex-msg :paths-fn paths-fn}
                       (if-some [resolved-var (try (requiring-resolve paths-fn)
                                                   (catch Exception _e nil))]
                         (let [{:as opts :keys [error paths]}
                               (try {:paths (cond-> @resolved-var (fn? @resolved-var) (apply []))}
                                    (catch Exception e
                                      {:error (str "An error occured invoking `" (pr-str resolved-var) "`: " (ex-message e))
                                       :paths-fn paths-fn}))]
                           (if error
                             opts
                             (if-not (sequential? paths)
                               {:error (str "`:paths-fn` must compute to a sequential value.")
                                :paths-fn paths-fn :resolved-paths paths}
                               {:resolved-paths paths})))
                         {:error ex-msg :paths-fn paths-fn})))
          index {:resolved-paths []})))

#_(resolve-paths {:paths ["notebooks/di*.clj"]})
#_(resolve-paths {:paths-fn 'clojure.core/inc})
#_(resolve-paths {:paths-fn 'nextjournal.clerk.builder/clerk-docs})

(defn expand-paths [build-opts]
  (let [{:as opts :keys [error resolved-paths]} (resolve-paths build-opts)]
    (if error
      opts
      (->> resolved-paths
           (mapcat (fn [path] (if (fs/exists? path)
                                [path]
                                (fs/glob "." path))))
           (filter (complement fs/directory?))
           (mapv (comp str fs/file))
           (hash-map :expanded-paths)
           (maybe-add-index build-opts)
           (ensure-not-empty build-opts)))))

#_(expand-paths {:paths ["notebooks/di*.clj"] :index "src/nextjournal/clerk/index.clj"})
#_(expand-paths {:paths ['notebooks/rule_30.clj]})
#_(expand-paths {:index "book.clj"})
#_(expand-paths {:paths-fn `clerk-docs})
#_(expand-paths {:paths-fn `clerk-docs-2})
#_(do (defn my-paths [] ["notebooks/h*.clj"])§
      (expand-paths {:paths-fn `my-paths}))
#_(expand-paths {:paths ["notebooks/viewers**"]})
