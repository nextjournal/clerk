(ns hooks)

(defmacro defhash
  [algorithm _digest-name]
  (let [fn-sym (symbol (name algorithm))]
    `(defn ~fn-sym
       [~'content]
       (multihash.core/create ~algorithm "foo"))))
