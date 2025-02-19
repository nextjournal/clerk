(ns viewers.int-set
  (:require [clojure.core :as b]
            [clojure.data.int-map :as i]))

(into (i/int-set) [1 2 3 4 4 5])


