(ns nextjournal.clerk.dev-launcher
  "A dev launcher that launches nrepl, shadow-cljs and once the first cljs compliation completes, Clerk.
  
  Avoiding other ns requires here so the REPL comes up early."
  (:require [nrepl.cmdline :as nrepl]
            [shadow.cljs.devtools.config :as config])
  (:import (java.lang.management ManagementFactory)
           (java.util Locale)))

(defn start [serve-opts]
  (future (nrepl/dispatch-commands {:middleware '[cider.nrepl/cider-middleware]}))
  (require 'shadow.cljs.silence-default-loggers)
  ((requiring-resolve 'shadow.cljs.devtools.server/start!))
  ((requiring-resolve 'shadow.cljs.devtools.api/watch)
   (-> (config/get-build! :viewer)
       (assoc :nextjournal.clerk/extra-namespaces (:extra-namespaces serve-opts))))
  ((requiring-resolve 'nextjournal.clerk/serve!) serve-opts)
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

(comment

 (do (shadow.cljs.devtools.api/stop-worker :viewer)
     (start {:extra-namespaces [] #_'[nextjournal.clerk.hello]}))
 )