(ns nextjournal.clerk.graph-visualizer
  (:require [arrowic.core :as arrowic]
            [weavejester.dependency :as dep]))

(defonce viewer
  (arrowic/create-viewer (arrowic/create-graph)))


(defn show-graph [{:keys [graph var->hash]}]
  (arrowic/view viewer
                (arrowic/with-graph (arrowic/create-graph)
                  (let [vars->verticies (into {} (map (juxt identity arrowic/insert-vertex!)) (keys var->hash))]
                    (doseq [var (keys var->hash)]
                      (doseq [dep (dep/immediate-dependencies graph var)]
                        (when (and (vars->verticies var)
                                   (vars->verticies dep))
                          (arrowic/insert-edge! (vars->verticies var) (vars->verticies dep)))))))))

#_(-> "notebooks/elements.clj" nextjournal.clerk.analyzer/build-graph show-graph)
#_(-> "src/nextjournal/clerk/analyzer.clj" nextjournal.clerk.analyzer/build-graph show-graph)
