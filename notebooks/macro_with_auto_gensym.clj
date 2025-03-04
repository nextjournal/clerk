(ns macro-with-auto-gensym)

(defmacro my-macro [x]
  `(let [x# ~x]
     (inc x#)))
