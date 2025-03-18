(ns qualified-methods)

(String/.length "foo")

(map String/.length ["f" "fo" "foo"])

String/1

Integer/parseInt ;; method value

String/CASE_INSENSITIVE_ORDER ;; field

;; TODO: get rid of reflection
(String/new "dude") ;; constructor

String/new

(map String/new ["dude"])

(map Integer/parseInt ["1" "2" "3"])

