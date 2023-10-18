(ns tailwind-extractor
  (:require [clojure.string :as str]
            [nbb.core :refer [await slurp]]
            ["fs" :as fs]
            ["fs$promises" :as fsp]
            ["glob$default" :as glob]
            ["process" :refer [chdir cwd]]
            ["tailwindcss/lib/lib/defaultExtractor.js" :refer [defaultExtractor]]))

(def context #js {:tailwindConfig #js {:separator ":" :prefix ""}})

(defn ->set [a] (new js/Set a))

(def extract-candidate-classes (comp to-array ->set (defaultExtractor context)))

(defn collect-candidate-classes [{:as state :keys [dest-file candidates files]}]
  (if-some [f (first files)]
    (.. (slurp f)
        (then (fn [content]
                (println "processing" f)
                (collect-candidate-classes (-> state
                                               (update :files next)
                                               (update :candidates (fnil into #{}) (extract-candidate-classes content)))))))
    (do
      (when dest-file (await (fsp/appendFile dest-file (str/join "\n" candidates))))
      (println (str "Extracted " (count candidates) " candidates.")))))

(defn -main [& args]
  (chdir "..")
  (let [[command dest-file] args]
    (case command

      "collect"
      (let [files (glob/sync "**/**.{clj,cljs,cljc}")]
        (when (and dest-file (fs/existsSync dest-file))
          (await (fsp/rm dest-file)))
        (println (str "Processing " (count files) " files…"))
        (collect-candidate-classes {:dest-file dest-file
                                    :files files}))
      "extract"
      (do
        (assert dest-file)
        (await (.then (slurp dest-file)
                      (fn [txt] (println :candidate-classes (str/join "\n" (extract-candidate-classes txt)))))))

      ;; else
      (println "Usage:\n  yarn nbb -m tailwind-extractor <collect|extract> target-file"))))
