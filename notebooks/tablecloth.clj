;; # Tablecloth Sample
;; Adapted from [playground.clj](https://github.com/scicloj/tablecloth/blob/9ed00539e5f0ddfde7e51afa111d573bef620042/playground.clj)

(ns tablecloth
  (:require [tablecloth.api :as tc]
            [nextjournal.clerk :as clerk]))

(defn cartesian-product
  [xxs]
  (if (seq xxs)
    (for [n (cartesian-product (rest xxs))
          x (first xxs)]
      (conj n x))
    '(nil)))

(defn expand-grid
  [in]
  (-> (map (partial zipmap (keys in))
           (cartesian-product (vals in)))
      (tc/dataset)))

(def input {:height (range 60 81 5)
            :weight (range 100 301 50)
            :sex [:Male :Female]})


(expand-grid input)

(tech.v3.datatype/->array-buffer (range 100))

#_(clerk/show! "notebooks/tablecloth.clj")
