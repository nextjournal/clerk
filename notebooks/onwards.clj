;; # Onwards 🏔
;; Notes about what currently breaks 💥 and what could be better tomorrow.
;; - [x] Lists spacing is broken
;; - [x] Nested lists are not formatted correctly
;; - [x] Errors are global, but should be local
;; - [x] Printing a var
;; - [x] Switch to fast serialization using Nippy
;; - [x] Store results as CAS files, only write when needed
;; - [x] Don't thaw result if it's already in memory
;; - [x] Don't cache things that are fast to evaluate
;; - [x] Handle conditional read in cljc files
;; - [x] Add README
;; - [ ] Make viewer registration local (on form + namespace)
;; - [x] Let the viewer opt into pagination
;; - [ ] Return complete datastructure if reasonably small (larger than the 20 elements we currently serve)
;; - [ ] A browser refresh is currently needed to reset a viewer override like in `notebooks/rule_30.clj`, otherwise it will carry over the viewer override to other notebooks that do no specify an override
;; - [ ] Evaluation error messages are poorly formatted
;; - [ ] Viewer errors are not displayed correctly
;; - [x] `first-generation` in `notebooks/rule_30.clj` renders incorrectly because of pagination type coercion from vector to list
;; - [x] `(def r (range 100))` in `notebooks/pagination.clj` shows incorrect `count`
;; - [ ] Figure out distributed caching using CAS + cloud bucket
;; - [x] Markdown should support Nextjournal Flavored Markdown including:
;;   - [x] TODO lists 😹
;;   - [x] Inline Formulas $E^2=m^2+\vec{p}^2$
;; - [x] Fix jar hashing (contents, not filename)
;; - [x] Printing of very large values
;; - [x] Make viewer api open with predicates
;; - [ ] Status log
;; - [ ] Datafy + Nav
;; - [ ] Allow to pin notebook
