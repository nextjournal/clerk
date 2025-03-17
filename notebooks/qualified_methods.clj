(ns qualified-methods)

(String/.length "foo")

(map String/.length ["f" "fo" "foo"])

String/1

Integer/parseInt ;; method value

String/CASE_INSENSITIVE_ORDER ;; field

;; TODO:
;; (String/new "dude")

;; TODO
;; (map String/new ["dude"])

;; TODO
;; (map Integer/parseInt ["1" "2" "3"])
