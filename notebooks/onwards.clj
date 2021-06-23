;; # Onwards 🏔
;; Notes about what currently breaks 💥 and what could be better tomorrow.
(ns errors)

;; - [x] Lists spacing is broken
;; - [x] Nested lists are not formatted correctly
;; - [x] Errors are global, but should be local
;; - [x] Printing a var
;; - [ ] Don't thaw result if it's already in memory
;; - [ ] Don't cache things that are fast to evaluate
;; - [ ] Figure out distributed caching using CAS + cloud bucket
;; - [ ] Markdown should support Nextjournal Flavored Markdown including:
;;   - [ ] TODO lists 😹
;;   - [ ] Inline Formulas $E^2=m^2+\vec{p}^2$
;; - [ ] Error display
;; - [ ] Fix jar hashing (contents, not filename)
;; - [x] Printing of very large values
;; - [ ] Datafy + Nav
;; - [ ] Allow to pin notebook
;; - [ ] Make viewer api open with predicates


;; Error: _No reader function for tag namespace._ Splice the comment to see the error.
(find-ns 'clojure.core)
