(ns nextjournal.clerk.render.hashing
  "Computes a hash based for Clerk's render cljs bundle."
  {:no-doc true}
  (:require [nextjournal.clerk.render.cas :as cas]
            [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.dejavu :as djv]))

(def output-dirs ["resources/public/ui"
                  "resources/public/build"])

(defn file-set [base-dir]
  (reduce into
          []
          [(mapv #(fs/file base-dir %) ["deps.edn"
                                        "render/deps.edn"
                                        "shadow-cljs.edn"
                                        "yarn.lock"])
           (djv/cljs-files (mapv #(fs/file base-dir %) ["src" "resources"]))]))

#_(file-set (fs/file "."))
#_(System/setProperty "nextjournal.dejavu.debug" "1")

(defn front-end-hash []
  (let [base-dir (let [resource (io/resource "nextjournal/clerk.clj")]
                   (when (= "file" (.getProtocol resource))
                     (-> (fs/file resource) fs/parent fs/parent fs/parent)))]
    (when-not base-dir
      (throw (ex-info "Clerk could note compute `font-end-hash` for cljs bundle." {:base-dir base-dir})))
    (str (djv/file-set-hash base-dir (file-set base-dir)))))

(defn build+upload-viewer-resources []
  (let [front-end-hash (front-end-hash)]
    (when-not (cas/tag-exists? {:tag front-end-hash})
      ((requiring-resolve 'babashka.tasks/run) 'build:js)
      (let [{:keys [manifest-path]} (cas/cas-put {:path "build"})]
        (cas/tag-put {:auth-token (System/getenv "GITHUB_TOKEN")
                      :namespace "nextjournal"
                      :tag front-end-hash
                      :target (str "/tree/" manifest-path)})))))
