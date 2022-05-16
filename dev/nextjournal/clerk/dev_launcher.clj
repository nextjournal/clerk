(ns nextjournal.clerk.dev-launcher
  "A dev launcher that launches nrepl, shadow-cljs and once the first cljs compliation completes, Clerk.

  Avoiding other ns requires here so the REPL comes up early."
  (:require [nrepl.cmdline :as nrepl]
            [shadow.cljs.devtools.config :as shadow.config])
  (:import (java.lang.management ManagementFactory)
           (java.util Locale)))

(defn start [{:as opts :keys [shadow-nrepl? load-cider-middleware?] :or {load-cider-middleware? true}}]
  (println "Starting Clerk with options: " opts)
  (when-not shadow-nrepl?
    (future (nrepl/dispatch-commands (when load-cider-middleware? {:middleware '[cider.nrepl/cider-middleware]}))))
  (require 'shadow.cljs.silence-default-loggers)
  ((requiring-resolve 'shadow.cljs.devtools.server/start!)
   (cond-> (shadow.config/load-cljs-edn)
     shadow-nrepl?
     (dissoc :nrepl)))
  ((requiring-resolve 'shadow.cljs.devtools.api/watch) :browser)
  ((requiring-resolve 'nextjournal.clerk/serve!) opts)
  (println "Clerk dev system ready in"
           (String/format (Locale. "en-US")
                          "%.2fs"
                          (to-array [(/ (.. ManagementFactory getRuntimeMXBean getUptime) 1000.0)]))))
