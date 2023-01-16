(ns nextjournal.clerk.graph-visualizer
  {:no-doc true}
  (:require [arrowic.core :as arrowic]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.analyzer :as analyzer]
            [weavejester.dependency :as dep]))

(defn dep-svg [{:keys [graph ->analysis-info]}]
  (clerk/html
   (arrowic/as-svg
    (arrowic/with-graph (arrowic/create-graph)
      (let [vars->verticies (into {} (map (juxt identity arrowic/insert-vertex!)) (keys ->analysis-info))]
        (doseq [var (keys ->analysis-info)]
          (doseq [dep (dep/immediate-dependencies graph var)]
            (when (and (vars->verticies var)
                       (vars->verticies dep))
              (arrowic/insert-edge! (vars->verticies var) (vars->verticies dep))))))))))


(defn graph-file [file]
  (-> (analyzer/analyze-file {:graph (dep/graph)} file )
      analyzer/build-graph
      dep-svg))

^{::clerk/width :full}
(graph-file "src/nextjournal/clerk/classpath.clj")


^{::clerk/width :full}
(graph-file "src/nextjournal/clerk/parser.cljc")
