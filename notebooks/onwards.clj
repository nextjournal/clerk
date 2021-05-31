;; # Onwards 🏔
;; Notes about what currently breaks 💥 and what could be better tomorrow.
(ns errors)

;; - [x] Lists spacing is broken
;; - [x] Nested lists are not formatted correctly
;; - [ ] Markdown should support Nextjournal Flavored Markdown including:
;;   - [ ] TODO lists 😹
;;   - [ ] Inline Formulas $E^2=m^2+\vec{p}^2$
;; - [x] Errors are global, but should be local
;; - [x] Printing a var
;; - [ ] Error display
;; - [ ] Fix jar hashing (contents, not filename)
;; - [ ] Make viewer api open with predicates
;; - [ ] Printing of very large values
;; - [ ] Datafy + Nav
;; - [ ] Allow to pin notebook


;; Error: _No reader function for tag namespace._ Splice the comment to see the error.
(find-ns 'clojure.core)
