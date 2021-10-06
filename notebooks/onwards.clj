;; # 🏔 Onwards
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
;; - [x] Markdown should support Nextjournal Flavored Markdown including:
;;   - [x] TODO lists 😹
;;   - [x] Inline Formulas $E^2=m^2+\vec{p}^2$
;; - [ ] Fix analyzer error for aliases requires w/o ns (e.g. in viewers notebooks)
;; ## 🕵🏻‍♀️ Viewers
;; - [x] Make viewer api open with predicates
;; - [x] Make viewer registration local on namespace
;; - [x] Make js-only viewers like plotly & vega work
;; - [x] Fix maps
;; - [x] Sort maps
;; - [x] Lazy seqs
;; - [x] Fix Rule 30 maps
;; - [x] Drop vector brackets when lazy load map element
;; - [x] Review viewer registration api
;; - [x] Simplify viewer api (drop `view-as` & change argument order in `with-viewer`/`s`)
;; - [x] Fix seeing map with blob-id in Rule 30 notebook
;; - [x] Turn `with-viewers` into a macro and make it take same unquoted form as `set-viewers!`
;; - [ ] Consistently use `Fn+Form` to carry viewer functions
;; - [ ] Show sci eval error when viewers error on eval
;; - [ ] Make map-viewer pass down options to select map-entry renderer for children and remove viewer from Rule 30
;; - [x] Drop lazy loading attempts in plotly + vega viewers
;; - [ ] Keep expanded state when lazy loading
;; - [ ] Allow to control viewer expansion state programmatically
;; - [ ] Move update opts fn to viewer map
;; - [x] Restrict string length + enable lazy loading
;; - [ ] Datafy + Nav
;; - [ ] Metadata viewer
;; - [ ] Persist viewer expansion state across reloads
;; - [x] Let the viewer opt into pagination
;; - [ ] Viewer errors are not displayed correctly
;; - [x] A browser refresh is currently needed to reset a viewer override like in `notebooks/rule_30.clj`, otherwise it will carry over the viewer override to other notebooks that do no specify an override
;; - [x] Evaluation error messages are poorly formatted
;; - [ ] Make stacktraces clickable
;; - [x] `first-generation` in `notebooks/rule_30.clj` renders incorrectly because of pagination type coercion from vector to list
;; - [x] `(def r (range 100))` in `notebooks/pagination.clj` shows incorrect `count`
;; - [x] Printing of very large values
;; ## 🚀 Private Beta
;; - [x] Move to non-jit compiled tailwind stylesheet, purging doesn't work nicely with custom viewers
;; - [ ] Make static build work
;; - [x] Review caching api
;; - [x] Change `:clerk/no-cache` to `:nextjournal.clerk/no-cache`
;; - [ ] Support setting `:nextjournal.clerk/no-cache` on namespaces as well
;; - [ ] Support setting `:nextjournal.clerk/cache-dir` on namespace
;; - [ ] Move public viewer api to `nextjournal.clerk` and use `(:require [nextjournal.clerk :as clerk])` in all example notebooks
;; - [ ] Release to maven & invite contributors
;; ## 💡 Ideas
;; - [ ] Hook up distributed caching using CAS + cloud bucket
;; - [ ] Status log
;; - [ ] Allow to pin notebook
