(ns tasks
  "Functions used in bb.edn"
  (:require
   [babashka.deps :as deps]
   [babashka.fs :as fs]
   [babashka.tasks :refer [shell]]
   [clojure.string :as str]
   [shared :refer [rev-count meta-edn]]))

(defn viewer-css-path []
  (let [cp (str/trim (with-out-str (deps/clojure ["-A:sci" "-Spath"])))]
    (str/trim (:out (shell {:out :string} (str "bb -cp " cp " -e '(println (.getPath (clojure.java.io/resource \"css/viewer.css\")))'"))))))

(defn update-meta []
  (let [rc (rev-count)
        m (meta-edn)
        v (:version m)
        new-version (assoc v :rev-count rc)
        new-meta (assoc m :version new-version)]
    (spit (doto (fs/file "resources/META-INF/nextjournal/clerk/meta.edn")
            (-> fs/parent fs/create-dirs)) new-meta)))

(defn today []
  (.format
   (java.time.LocalDate/now)
   (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd")))

(defn update-changelog []
  (->> (str/replace (slurp "CHANGELOG.md")
                    (re-pattern "## [Uu]nreleased")
                    (str "## Unreleased\n\n...\n\n"
                         (format "## %s (%s)"
                                 (shared/version)
                                 (today))))
       (spit "CHANGELOG.md")))

(defn update-readme []
  (->> (str/replace (slurp "README.md")
                    (re-pattern "\\{io.github.nextjournal/clerk \\{:mvn/version \".+\"\\}\\}")
                    (str "{io.github.nextjournal/clerk {:mvn/version " (pr-str (shared/version)) "}}"))
       (spit "README.md")))

(defn tag []
  (let [tag (str "v" (shared/version))]
    (shell "git tag" tag)))

(defn publish []
  (update-meta)
  (update-changelog)
  (update-readme)
  (shell "git add"
         "resources/META-INF"
         "README.md"
         "CHANGELOG.md")
  (shell (str "git commit -m v" (shared/version)))
  (tag)
  (println "\n\nRun:\n\n" "  git push --atomic"
           "origin" "main" (str "v" (shared/version))
           "\n\nto push the release and let CI build it!"))

(defn latest-sha []
  (shared/latest-sha))

(defn version []
  (shared/version))
