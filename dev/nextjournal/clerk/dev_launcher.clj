(ns nextjournal.clerk.dev-launcher
  "A dev launcher that launches nrepl, shadow-cljs and once the first cljs compliation completes, Clerk.
  
  Avoiding other ns requires here so the REPL comes up early."
  (:require [nrepl.cmdline :as nrepl]))

(defn start [serve-opts]
  (future (nrepl/dispatch-commands {:middleware '[cider.nrepl/cider-middleware]}))
  (require 'shadow.cljs.silence-default-loggers)
  ((requiring-resolve 'shadow.cljs.devtools.server/start!))
  ((requiring-resolve 'shadow.cljs.devtools.api/watch) :browser)
  ((requiring-resolve 'nextjournal.clerk/serve!) serve-opts))
