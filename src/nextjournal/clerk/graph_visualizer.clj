(ns nextjournal.clerk.graph-visualizer
  {:no-doc true :nextjournal.clerk/no-cache true}
  (:require [arrowic.core :as arrowic]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.parser :as parser]
            [weavejester.dependency :as dep]))

(defn dep-svg [{:keys [graph ->analysis-info]}]
  (clerk/html
   (arrowic/as-svg
    (arrowic/with-graph (arrowic/create-graph)
      (let [vars->verticies (into {} (map (juxt identity arrowic/insert-vertex!)) (keys ->analysis-info))]
        (assert (not-empty vars->verticies))
        (doseq [var (keys ->analysis-info)]
          (doseq [dep (dep/immediate-dependencies graph var)]
            (when (and (vars->verticies var)
                       (vars->verticies dep))
              (arrowic/insert-edge! (vars->verticies var) (vars->verticies dep))))))))))

(defn file->graph [file] (analyzer/build-graph (parser/parse-file file)))

(defn graph-file
  ([file] (graph-file {} file))
  ([{:keys [key-filter-fn]} file]
   (-> (file->graph file)
       (cond-> (ifn? key-filter-fn)
         (update :->analysis-info (partial into {} (filter (comp key-filter-fn key)))))
       dep-svg)))

^{::clerk/width :full}
(graph-file "src/nextjournal/clerk/classpath.clj")

^{::clerk/width :full}
(graph-file {:key-filter-fn (comp #{"nextjournal.clerk.parser"
                                    "nextjournal.clerk.analyzer"
                                    "nextjournal.clerk.classpath"
                                    "nextjournal.clerk.config"} namespace)}
            "src/nextjournal/clerk/analyzer.clj")
