(ns cljs-render-fn-file
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]))

(defn render-fn [sym]
  (let [ns (namespace sym)
        file (str (str/replace (munge ns) "." "/") ".cljs")
        resource (io/resource file)
        source (slurp resource)
        source `(do
                  (load-string ~source)
                  (resolve ~(list 'quote sym)))]
    source))

^::clerk/no-cache
(clerk/with-viewer {:render-fn (render-fn 'cljs-render-fn-source/render-fn)
                    :fetch-fn (fn [_ x] x)}
  [{:a 1} ])
