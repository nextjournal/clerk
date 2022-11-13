(ns nextjournal.clerk.dev-launcher
  "A dev launcher that launches nrepl, shadow-cljs and once the first cljs compliation completes, Clerk.
  
  Avoiding other ns requires here so the REPL comes up early."
  (:require [nextjournal.clerk.builder.cljs :as builder]))


(defn start [serve-opts]
  (builder/start-watch serve-opts))