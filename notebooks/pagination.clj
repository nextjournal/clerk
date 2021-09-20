;; # Pagination
#_(nextjournal.clerk/show! "notebooks/pagination.clj")

(range)

(def notebooks
  (clojure.java.io/file "notebooks"))

#_
(slurp "/usr/share/dict/words")

[notebooks]

(into #{} (map str) (file-seq notebooks))

(def r (range 100))

(map inc r)

[(mapv inc r)]

^:clerk/no-cache (shuffle r)

;; A long list.
(range 1000)

;; A somewhat large map.
(zipmap (range 1000) (map #(* % %) (range 1000)))


^:clerk/no-cache (shuffle (range 42))
