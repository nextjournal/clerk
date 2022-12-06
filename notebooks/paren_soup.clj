;; # 🍲 Paren Soup
{:foo (range 30) :bar (range 20)}

(map range (range 30))

{1 2}

[[1] 2]


[1 [2]]

[[[1] 2] 3]

[1 [2 [3]]]

[1 [2] 3]


[1 [2] #{[3]}]


[1 [2] #{[3]} 4]


{:foo [1 [2] #{[3] [4] [5 6 7 8]}]}


{:foo [1 [2] [[3] [4] [5 6 7 8] 9]]}

{1 [2] 3 [4]}

{:a "a\nb"}

[1 "a\nb"]

[[1 "a\nb"]]

{:a 1 :b [[3 4 "aaaa\nbbb\nccc\ndd\ne"]]}

{:a 1 :b [[3 4 (apply str (cons "a\n" (repeat 80 "b")))]]}

#_(nextjournal.clerk/build-static-app! {:paths ["notebooks/paren_soup.clj"]})
#_(nextjournal.clerk/show! "notebooks/paren_soup.clj")
