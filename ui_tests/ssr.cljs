(ns ssr
  "Dev helper to run server-side-rendering using Node.

  Use this to iterate on it, then make sure the advanced bundle works
  in Graal via `nextjournal.clerk.ssr`."
  (:require ["./../public/js/viewer.js"]
            ;; the above is the dev build, the one below the release (generate it via `bb release:js`)
            #_["./../build/viewer.js" :as viewer]
            [babashka.cli :as cli]
            [nbb.core :refer [slurp]]
            [promesa.core :as p]))

(defn -main [& args]
  (p/let [{:keys [file edn url]} (:opts (cli/parse-args args {:alias {:u :url :f :file}}))
          edn-string (cond file (slurp file)
                           edn edn)]
    (if edn-string
      (println (js/nextjournal.clerk.sci_env.ssr edn-string))
      (binding [*print-fn* *print-err-fn*]
        (println "must provide --file or --edn arg")))))


