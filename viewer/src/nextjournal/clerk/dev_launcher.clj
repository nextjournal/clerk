(ns nextjournal.clerk.dev-launcher
  "A dev launcher that launches nrepl, shadow-cljs and once the first cljs compliation completes, Clerk.
  
  Avoiding other ns requires here so the REPL comes up early."
  (:require [nrepl.cmdline :as nrepl]
            [clojure.java.io :as io]
            [shadow.cljs.devtools.config :as config]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow.server]
            [nextjournal.clerk :as clerk])
  (:import (java.lang.management ManagementFactory)
           (java.util Locale)))

(defn get-config [opts]
  (-> (io/resource "nextjournal/clerk/shadow-cljs.edn")
      config/read-config
      (assoc-in [:builds :viewer :output-dir] (str (:out-path opts) "/js"))
      (assoc-in [:builds :viewer :release :output-dir] (str (:out-path opts) "/js"))
      config/normalize
      (->> (merge config/default-config))))

(defn get-build [opts]
  (-> (get-config opts)
      (config/get-build :viewer)
      (assoc :nextjournal.clerk/extra-namespaces (:extra-namespaces opts))))

(defn start [serve-opts]
  (future (nrepl/dispatch-commands {:middleware '[cider.nrepl/cider-middleware]}))
  (require 'shadow.cljs.silence-default-loggers)

  (shadow.server/start! (get-config serve-opts))
  (shadow/watch (get-build serve-opts))
  (clerk/serve! serve-opts)

  (set! *print-namespace-maps* false)
  (println "Clerk dev system ready in"
           (String/format (Locale. "en-US")
                          "%.2fs"
                          (to-array [(/ (.. ManagementFactory getRuntimeMXBean getUptime) 1000.0)]))))

(defn extra-namespaces
  {:shadow.build/stage :configure}
  [state]
  (update-in state [:shadow.build.modules/config :viewer :entries]
             into
             (-> state
                 :shadow.build/config
                 :nextjournal.clerk/extra-namespaces)))

(defn release [clerk-opts shadow-opts]
  (shadow/with-runtime
   (shadow/release*
    (get-build clerk-opts)
    shadow-opts)))

(comment

 (do (shadow.cljs.devtools.api/stop-worker :viewer)
     (start {:extra-namespaces [] #_'[nextjournal.clerk.hello]}))
 )