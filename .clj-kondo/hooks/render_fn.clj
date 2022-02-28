(ns hooks.render-fn
  (:require [clj-kondo.hooks-api :as api]))

(defn render-fn [{:keys [node]}]
  {:node (api/list-node (list* (api/token-node 'clojure.core/fn)
                               (rest (:children node))))})
