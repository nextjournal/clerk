(ns nextjournal.clerk.dev-launcher
  (:require [nextjournal.clerk :as clerk]))

(defonce !start-clerk-delay
  (atom nil))

(defn shadow-hook-cljs-flushed
  {:shadow.build/stage :flush}
  [build-state & _]  
  (some-> !start-clerk-delay deref deref)
  build-state)

(defn start [{:keys [shadow-cli-args serve-opts]}]
  (if-let [shadow-cli-main (and (seq shadow-cli-args)
                                (try (requiring-resolve 'shadow.cljs.devtools.cli-actual/main)
                                     (catch Exception e
                                       (binding [*out* *err*]
                                         (prn e)
                                         (System/exit 1)))))]
    (do
      (reset! !start-clerk-delay (delay (clerk/serve! serve-opts)))
      (apply shadow-cli-main shadow-cli-args))
    (clerk/serve! serve-opts)))
