(ns browser-nrepl-client
  (:require [babashka.nrepl-client :as nrepl]))

(defn -main [& [expr]]
  (prn (nrepl/eval-expr {:port 1339 :expr expr})))
