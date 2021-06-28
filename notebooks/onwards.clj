;; # Onwards 🏔
;; Notes about what currently breaks 💥 and what could be better tomorrow.
;; - [x] Lists spacing is broken
;; - [x] Nested lists are not formatted correctly
;; - [x] Errors are global, but should be local
;; - [x] Printing a var
;; - [x] Switch to fast serialization using Nippy
;; - [x] Store results as CAS files, only write when needed
;; - [ ] Don't thaw result if it's already in memory
;; - [x] Don't cache things that are fast to evaluate
;; - [ ] Figure out distributed caching using CAS + cloud bucket
;; - [ ] Markdown should support Nextjournal Flavored Markdown including:
;;   - [ ] TODO lists 😹
;;   - [ ] Inline Formulas $E^2=m^2+\vec{p}^2$
;; - [ ] Better Error display
;; - [x] Fix jar hashing (contents, not filename)
;; - [x] Printing of very large values
;; - [ ] Status log
;; - [ ] Datafy + Nav
;; - [ ] Allow to pin notebook
;; - [ ] Make viewer api open with predicates
