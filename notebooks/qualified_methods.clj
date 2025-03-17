(ns qualified-methods)

(String/.length "foo")

(map String/.length ["f" "fo" "foo"])

String/1

Integer/parseInt ;; method value

String/CASE_INSENSITIVE_ORDER ;; field

(String/new "dude") ;; constructor

String/new

(map String/new ["dude"])

(map Integer/parseInt ["1" "2" "3"])

(throw (ex-info "dude" {})) ;; this if CI fails
