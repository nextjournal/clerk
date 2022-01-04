(ns circular)

(declare a)
(def b (str a " boom"))
(def a (str "boom " b))
