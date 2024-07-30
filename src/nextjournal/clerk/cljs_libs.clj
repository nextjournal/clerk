(ns nextjournal.clerk.cljs-libs
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.namespace.dependency :as tnsd]
   [clojure.tools.namespace.parse :as tnsp]
   [edamame.core :as e]))

(defonce ^:private cljs-graph (atom (tnsd/graph)))

(defn- ns->resource [ns]
  (io/resource (-> (namespace-munge ns)
                   (str/replace "." "/")
                   (str ".cljs"))))

(defn require-cljs [ns]
  (let [cljs-file (ns->resource ns)
        ns-decl (with-open [rdr (e/reader (io/reader cljs-file))]
                  (tnsp/read-ns-decl rdr))
        nom (tnsp/name-from-ns-decl ns-decl)
        deps (tnsp/deps-from-ns-decl ns-decl)]
    (run! require-cljs deps)
    (swap! cljs-graph (fn [graph]
                        (reduce (fn [acc dep]
                                  (tnsd/depend acc nom dep))
                                graph deps)))
    nil))

(defn update-blocks [doc]
  (update doc :blocks (fn [blocks]
                        (concat
                         (let [resources (map ns->resource (tnsd/topo-sort @cljs-graph))]
                           (map (fn [resource]
                                  (let [code-str (slurp resource)]
                                    {:type :code
                                     :text (pr-str `(nextjournal.clerk/eval-cljs-str ~code-str))}))
                                resources))
                         blocks))))
