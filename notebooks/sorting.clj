^:nextjournal.clerk/no-cache
(ns sorting)

;; # 📶 Sorting

#{3 1 2}

[:a 1 "b" 2 :c 3 "a" 4 'b 5 'a 6 \z 7 \a 8]

{:a 1 "b" 2 :c 3 "a" 4 'b 5 'a 6 \z 7 \a 8}

#{{:a 1} {:b 2} {:c 3} {:d 4}}

{[1 2] 5 [2] 2 [1] 1  [1 1] 4 [3] 3}


(comment
  (nextjournal.clerk/serve {:watch-paths ["notebooks"]})
  (nextjournal.clerk/show! "notebooks/sorting.clj")
  )
