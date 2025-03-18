(ns clojure-1-12.clj
  {:nextjournal.clerk/no-cache true})

(String/.length "foo")

(map String/.length ["f" "fo" "foo"])

String/1

Integer/parseInt ;; method value

String/CASE_INSENSITIVE_ORDER ;; field

(String/new "dude") ;; constructor

^[String] String/new

(map ^[String] String/new ["dude"])

(map Integer/parseInt ["1" "2" "3"])
