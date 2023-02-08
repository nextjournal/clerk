(ns nextjournal.clerk.render.cas
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.cas-proxy.cas.hashing :as h]
            [cheshire.core :as json]
            [babashka.fs :as fs]
            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]
            [ring.util.codec :as ring-codec]))

;; Change default client for the whole application:
;; Needed for TLS connections using SNI
(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

(def ^:dynamic *cas-host* "https://cas.clerk.garden")

(defn cas-url [{:as opts
                :keys [host key]
                :or {host *cas-host*}}]
  (str host "/" key
       (let [query-params (ring-codec/form-encode (select-keys opts [:filename :content-type]))]
         (when (not (str/blank? query-params))
           (str "?" query-params)))))

(defn cas-exists? [opts]
  (-> @(http/head (cas-url opts))
      :status
      (= 200)))

(defn cas-get [opts]
  (-> @(http/get (cas-url opts))
      :body
      slurp))

(defn cas-put [{:keys [path host]
                :or {host *cas-host*}}]
  (let [f (io/file path)
        prefix (if (fs/directory? path) (str f "/") "")
        files (->> (file-seq f)
                   (filter fs/regular-file?)
                   (map (fn [f] (let [path (str (fs/path f))
                                      hash (with-open [s (io/input-stream f)]
                                             (h/hash s))]
                                  {:path (str/replace path prefix "")
                                   :hash hash
                                   :filename (fs/file-name path)
                                   :content f}))))
        {files-to-upload false files-already-uploaded true} (group-by #(cas-exists? {:key (:hash %)}) files)
        multipart (concat (map (fn [{:keys [path filename content]}]
                                 {:name path
                                  :filename filename
                                  :content content}) files-to-upload)
                          (map (fn [{:keys [path filename hash]}]
                                 {:name path
                                  :filename filename
                                  :content-type "application/clerk-cas-hash"
                                  :content hash}) files-already-uploaded))
        {:as res :keys [status body]} @(http/post host {:multipart multipart})]
    (if (= 200 status)
      (-> body
          (json/parse-string))
      res)))

(def ^:dynamic *tags-host* "https://storage.clerk.garden")

(defn tag-put [{:keys [host auth-token namespace tag target]
                :or {host *tags-host*}}]
  (-> @(http/post (str  "/" namespace "/" tag)
                  {:headers {"auth-token" auth-token
                             "content-type" "plain/text"}
                   :body target})))

(defn tag-url [{:keys [host namespace tag path]
                :or {host *tags-host*}}]
  (str host "/" namespace "/" tag (when path (str "/" path))))

(defn tag-get [{:as opts}]
  (-> @(http/get (tag-url opts))))

(defn tag-exists? [opts]
  (-> @(http/head (tag-url opts))
      :status
      (= 200)))
