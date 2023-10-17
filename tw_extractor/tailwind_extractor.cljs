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

(def extract (comp to-array ->set (defaultExtractor context)))

(defn tokenize [{:as state :keys [dest-file candidates files]}]
  (if-some [f (first files)]
    (.. (slurp f)
        (then (fn [content]
                (println "processing" f)
                (tokenize (-> state
                              (update :files next)
                              (update :candidates (fnil into #{}) (extract content)))))))
    (do
      (await (fsp/appendFile dest-file (str/join "\n" candidates)))
      (println (str "Extracted " (count candidates) " candidates.")))))

;; run with `yarn nbb -m tailwind-extractor tw-candidates.txt`
(defn -main [& args]
  (let [dest-file (or (first args) "test.txt")]
    (chdir "..")
    (when (fs/existsSync dest-file)
      (await (fsp/rm dest-file)))
    (tokenize {:dest-file dest-file
               :files (glob/sync "src/**/**.{clj,cljs,cljc}")})))
