(ns ssr
  "Dev helper to run server-side-rendering using Node.

  Use this to iterate on it, then make sure the advanced bundle works
  in Graal via `nextjournal.clerk.ssr`."
  (:require ["./../public/js/viewer.js" :as viewer]
            #_["./../build/viewer.js" :as viewer]
            [babashka.cli :as cli]
            [promesa.core :as p]
            [nbb.core :refer [slurp]]))

(defn -main [& args]
  (p/let [{:keys [file edn url]} (:opts (cli/parse-args args {:alias {:u :url :f :file}}))
          edn-string (cond file (slurp file)
                           edn edn)]
    (if edn-string
      (println (js/nextjournal.clerk.static_app.ssr edn-string))
      (binding [*out* *err*]
        (println "must provide --file or --edn arg")))))


