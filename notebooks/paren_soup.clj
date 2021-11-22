;; # 🍲 Paren Soup
(ns parens-soup)
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


#_(nextjournal.clerk/build-static-app! {:paths ["notebooks/paren_soup.clj"]})
#_(nextjournal.clerk/show! "notebooks/paren_soup.clj")
