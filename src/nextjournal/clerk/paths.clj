(ns nextjournal.clerk.paths
  "Clerk's paths expansion and paths-fn handling."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [nextjournal.clerk.git :as git])
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
      (cond-> (merge (select-keys build-opts [:paths :paths-fn :index]) opts)
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

(defn set-index-when-single-path [{:as opts :keys [expanded-paths]}]
  (cond-> opts
    (and (not (contains? opts :index))
         (= 1 (count expanded-paths)))
    (assoc :index (first expanded-paths))))

#_(set-index-when-single-path {:expanded-paths ["notebooks/rule_30.clj"]})
#_(set-index-when-single-path {:expanded-paths ["notebooks/rule_30.clj" "book.clj"]})

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
           (set-index-when-single-path)
           (ensure-not-empty build-opts)))))

#_(expand-paths {:paths ["notebooks/di*.clj"] :index "src/nextjournal/clerk/index.clj"})
#_(expand-paths {:paths ['notebooks/rule_30.clj] :index "notebooks/markdown.md"})
#_(expand-paths {:index "book.clj"})
#_(expand-paths {:paths-fn `nextjournal.clerk.builder/clerk-docs})
#_(expand-paths {:paths-fn `clerk-docs-2})
#_(do (defn my-paths [] ["notebooks/h*.clj"])§
      (expand-paths {:paths-fn `my-paths}))
#_(expand-paths {:paths ["notebooks/viewers**"]})


(defn read-opts-from-deps-edn! []
  (if (fs/exists? "deps.edn")
    (let [deps-edn (edn/read-string (slurp "deps.edn"))]
      (if-some [clerk-alias (get-in deps-edn [:aliases :nextjournal/clerk])]
        (get clerk-alias :exec-args
             {:error (str "No `:exec-args` found in `:nextjournal/clerk` alias.")})
        {:error (str "No `:nextjournal/clerk` alias found in `deps.edn`.")}))
    {:error (str "No `deps.edn` found in project.")}))

(def ^:dynamic *build-opts* nil)

(def build-help-link "\n\nLearn how to [set up your static build](https://book.clerk.vision/#static-building).")

(defn index-paths
  ([] (index-paths (or *build-opts* (read-opts-from-deps-edn!))))
  ([{:as opts :keys [index error]}]
   (if error
     (update opts :error str build-help-link)
     (let [{:as result :keys [expanded-paths error]} (if (contains? opts :expanded-paths) opts (expand-paths opts))]
       (if error
         (update result :error str build-help-link)
         {:paths (remove #{index "index.clj"} expanded-paths)})))))

#_(index-paths)
#_(index-paths {:paths ["CHANGELOG.md"]})
#_(index-paths {:paths-fn "boom"})

(defn process-paths [{:as opts :keys [paths paths-fn index]}]
  (merge (if (or paths paths-fn index)
           (expand-paths opts)
           opts)
         (git/read-git-attrs)))

#_(process-paths {:paths ["notebooks/rule_30.clj"]})
#_(process-paths {:paths ["notebooks/rule_30.clj"] :index "notebooks/links.md"})
#_(process-paths {:paths ["notebooks/no_rule_30.clj"]})
#_(v/route-index? (process-paths @!server))
#_(route-index (process-paths @!server) "")


(defn path-in-cwd
  "Turns `file` into a unixified (forward slashed) path if the is in the cwd,
  returns `nil` otherwise."
  [file]
  (when (and (string? file)
             (fs/exists? file))
    (let [rel (fs/relativize (fs/cwd) (fs/canonicalize file #{:nofollow-links}))]
      (when-not (str/starts-with? (str rel) "..")
        (fs/unixify rel)))))

#_(path-in-cwd "notebooks/rule_30.clj")
#_(path-in-cwd "/tmp/foo.clj")
#_(path-in-cwd "../scratch/rule_30.clj")

(defn drop-extension [file]
  (cond-> file
    (fs/extension file)
    (str/replace (re-pattern (format ".%s$" (fs/extension file))) "")))

#_(drop-extension "notebooks/rule_30.clj")
