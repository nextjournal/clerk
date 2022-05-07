(ns nextjournal.clerk.dev-launcher
  "A dev launcher that launches nrepl, shadow-cljs and once the first cljs compliation completes, Clerk.
  
  Avoiding ns requires here so the REPL comes up early.")

(defonce !start-clerk-delay
  (atom nil))

(defn cljs-flushed
  {:shadow.build/stage :flush}
  [build-state & _]
  (some-> !start-clerk-delay deref deref)
  build-state)

(defn start [serve-opts]
  (reset! !start-clerk-delay (delay ((requiring-resolve 'nextjournal.clerk/serve!) serve-opts)))
  (future ((requiring-resolve 'nrepl.cmdline/-main) "--middleware" "[cider.nrepl/cider-middleware]"))
  ((requiring-resolve 'shadow.cljs.devtools.server/start!))
  ((requiring-resolve 'shadow.cljs.devtools.api/watch) :browser))
