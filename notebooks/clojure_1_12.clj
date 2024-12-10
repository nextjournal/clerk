(ns clojure-1-12)

^{:nextjournal.clerk/visibility {:result :hide}}
(defn to-matrix [input]
  (for [line (String/.split input "\\n")
        :let [numbers (String/.split line "\\s+")]]
    (map parse-long #_Long/parseLong numbers)))

(to-matrix "1 1 2
4 5 6")
