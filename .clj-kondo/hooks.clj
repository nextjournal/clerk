(ns hooks)

(defmacro defhash
  [algorithm _digest-name]
  (let [fn-sym (symbol (name algorithm))]
    `(defn ~fn-sym
       [~'content]
       (multihash.core/create ~algorithm "foo"))))

(defn ->viewer-fn [{:keys [node]}]
  (let [[name-node quoted-node] (:children node)
        quoted-node {:tag :syntax-quote
                     :children (:children quoted-node)}]
    {:node (assoc node :children [name-node quoted-node])}))
