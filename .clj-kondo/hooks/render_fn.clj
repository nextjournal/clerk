(ns hooks.render-fn)

(defn render-fn [{:keys [node]}]
  (let [new-node (-> (second (:children node))
                     :children first)]
    {:node new-node}))
