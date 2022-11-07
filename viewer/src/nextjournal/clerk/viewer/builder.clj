(ns nextjournal.clerk.viewer.builder
  (:require [clojure.java.io :as io]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.config :as config]
            [shadow.cljs.devtools.server :as shadow.server]
            [shadow.cljs.devtools.server.runtime :as runtime]))

(defn get-config [opts]
  (-> (io/resource "nextjournal/clerk/shadow-cljs.edn")
      config/read-config
      (update-in [:builds :viewer :modules :viewer :entries] (fnil into []) (:extra-namespaces opts))
      (assoc-in [:builds :viewer :output-dir] (str (:out-path opts) "/js"))
      (assoc-in [:builds :viewer :release :output-dir] (str (:out-path opts) "/js"))
      config/normalize
      (->> (merge config/default-config))))

(defn get-build [opts]
  (-> (get-config opts)
      (config/get-build :viewer)))

(defn release!
  "Builds a release of Clerk viewer, returns asset path."
  [opts shadow-opts]
  (require 'shadow.cljs.silence-default-loggers)
  (let [server-running? (runtime/get-instance)
        _ (when-not server-running? (shadow.server/start! (get-config opts)))
        state (shadow/with-runtime
               (shadow/release*
                (get-build opts)
                shadow-opts))
        _ (when-not server-running? (shadow.server/stop!))
        output-name (->> (or (:shadow.build.closure/modules state)
                             (:build-modules state))
                         (filter (comp #{:viewer} :module-id))
                         first
                         :output-name)]
    (str "/js/" output-name)))