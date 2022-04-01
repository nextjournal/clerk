(ns nextjournal.test-runner
  (:require [nextjournal.clerk.hashing :as hashing]
            [clojure.set :as set]
            [nextjournal.beholder :as beholder]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.file :as file]
            [clojure.java.io :as io]))

(defn info [file]
  (let [analyzed-doc (-> file
                         hashing/parse-file
                         hashing/build-graph)]
    (assoc (:graph analyzed-doc) :->hash (->> (hashing/hash analyzed-doc)
                                              (filter (fn [[k _v]] (symbol? k)))
                                              (into {})))))

(defn load! [file]
  (require (second (file/read-file-ns-decl file)) :reload))

(defn test-vars-for-file [file]
  (into #{}
        (filter #(-> % meta :test)
                (vals (ns-interns (second (file/read-file-ns-decl file)))))))

(defonce !state (atom {}))

(defn init! [paths]
  (let [files (mapcat (comp find/find-sources-in-dir io/file) paths)]
    (println files)
    (run! load! files)
    (reset! !state
            (reduce (fn [acc f]
                      (-> acc
                          (update :test-vars #(set/union % (test-vars-for-file f)))
                          (assoc-in [:files (str f)] (info (str f)))))
                    {:files {}
                     :test-vars #{}}
                    files))))

(defn affected-test-vars [all-test-vars all-deps last-info new-info]
  (let [all-test-syms (into #{} (map symbol all-test-vars))
        changed (->> last-info
                     :->hash
                     (filter (fn [[k v]] (not= (get-in new-info [:->hash k]) v)))
                     (map first))]
    (->> changed
         (reduce (fn [test-vars changed-var]
                   (set/union test-vars
                              (set/intersection all-test-syms
                                                (get all-deps changed-var))))
                 #{})
         (map resolve)
         (into #{}))))

(defn update-file! [file]
  (let [filename     (str file)
        last-info    (get-in @!state [:files filename])
        test-vars    (set/union (test-vars-for-file file)
                                (get @!state :test-vars))
        new-info     (info filename)
        all-deps     (apply merge-with set/union (map (comp :dependents second) (:files @!state)))
        tests-to-run (affected-test-vars test-vars all-deps last-info new-info)]
    (if (= last-info new-info)
      (println "unchanged " filename)
      (do (println "reloading " filename)
          (load! file)))
    (when (seq tests-to-run)
      (println "tests to run " tests-to-run)
      (run! (fn [v] (println "running " v) (v)) tests-to-run))
    (swap! !state
           #(-> %
                (assoc :test-vars test-vars)
                (assoc-in [:files filename] new-info)))))

(defonce !watcher (atom nil))

(defn- halt-watcher! []
  (when-let [{:keys [watcher paths]} @!watcher]
    (println "Stopping old watcher for paths" (pr-str paths))
    (beholder/stop watcher)
    (reset! !watcher nil)))

(defn supported-file?
  "Returns whether `path` points to a file that should be shown."
  [path]
  (some? (re-matches #"(?!^\.#).+\.(clj|cljc)$" (.. path getFileName toString))))

(defn file-event [{:keys [type path]}]
  (when (and (contains? #{:modify :create} type)
             (supported-file? path))

    (binding [*ns* (find-ns 'user)]
      (try
        (#'update-file! (io/file (.. path toString)))
        (catch Exception e
          (println e))))))

(defn watch! [paths]
  (halt-watcher!)
  (init! paths)
  (when (seq paths)
    (println "Starting new watcher for paths" (pr-str paths))
    (reset! !watcher {:paths paths
                      :watcher (apply beholder/watch #(#'file-event %) paths)})))

; (init! ["src" "test"])
; (do (update-file! "src/nextjournal/clerk/hashing.clj") nil)
; (update-file! "test/nextjournal/clerk/hashing_test.clj")
; (watch! ["src" "test"])
