;; Boom 💥
;; This will blow up shortly.
(throw (ex-info "boom!" {:rand-int (rand-int 1000) :range-100 (range 100)}))
