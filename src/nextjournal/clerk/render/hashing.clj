(ns nextjournal.clerk.render.hashing
  "Computes a hash based for Clerk's render cljs bundle."
  {:no-doc true}
  (:require [babashka.fs :as fs]
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

(defn assets-tag []
  (str "clerk-assets@" (front-end-hash)))

(defn dynamic-asset-map []
  {"/js/viewer.js" (str "https://storage.clerk.garden/nextjournal/" (assets-tag) "/viewer.js?immutable=true")})
