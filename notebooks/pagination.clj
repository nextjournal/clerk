;; # Pagination
#_(require '[nextjournal.viewer :as v])

(def notebooks
  (clojure.java.io/file "notebooks"))

[(clojure.java.io/file "notebooks")]

(sort (into #{} (map str) (file-seq notebooks)))

(def r (range 100))

(map inc r)

(mapv inc r)
#_

^:clerk/no-cache (shuffle r)

#_#_#_
;; A long list.
(range 1000)

;; A somewhat large map.
(zipmap (range 1000) (map #(* % %) (range 1000)))



^:clerk/no-cache (shuffle (range 42))


#_
(v/register-viewer! :vector {:n 20} (fn [x options]
                                      (v/html (into [:div.flex.flex-col] (map (partial v/inspect options)) x))))
