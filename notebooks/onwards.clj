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
;; - [x] Fix analyzer error for aliases requires w/o ns (e.g. in viewers notebooks)
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
;; - [x] Consistently use `Fn+Form` to carry viewer functions
;; - [x] Make map-viewer pass down options to select map-entry renderer for children and remove viewer from Rule 30
;; - [x] Drop lazy loading attempts in plotly + vega viewers
;; - [x] Keep expanded state when lazy loading
;; - [x] Move update opts fn to viewer map
;; - [x] Restrict string length + enable lazy loading
;; - [x] Let the viewer opt into pagination
;; - [x] A browser refresh is currently needed to reset a viewer override like in `notebooks/rule_30.clj`, otherwise it will carry over the viewer override to other notebooks that do no specify an override
;; - [x] Evaluation error messages are poorly formatted
;; - [x] `first-generation` in `notebooks/rule_30.clj` renders incorrectly because of pagination type coercion from vector to list
;; - [x] `(def r (range 100))` in `notebooks/pagination.clj` shows incorrect `count`
;; - [x] Printing of very large values
;; ### 👁 More Viewers
;; - [ ] Binary / Hex
;; - [ ] Image
;; ## 🚀 Private Beta
;; - [x] Move to non-jit compiled tailwind stylesheet, purging doesn't work nicely with custom viewers
;; - [x] Make static build work
;; - [x] Review caching api
;; - [x] Change `:clerk/no-cache` to `:nextjournal.clerk/no-cache`
;; - [x] Move public viewer api to `nextjournal.clerk` and use `(:require [nextjournal.clerk :as clerk])` in all example notebooks
;; ## 💒 Open Source Release
;; - [x] Allow to pin notebook
;; - [x] Release to maven & invite contributors
;; - [x] Fix error when describing Datomic entities
;; - [x] Limit description to make large datasets work
;; - [x] Debug & fix extra calls to inspect / rendering
;; - [x] Setup up static builds
;; - [x] Fix [link](#) style when code cell uses markdown viewer
;; - [x] Do pass over README
;; - [x] Add CHANGELOG
;; - [x] Release 0.2
;; - [x] Make github repo public
;; ## 🛠 Up Next
;; - [x] Let consumers (SICMUitls) extend sci ns by putting it into an atom
;; - [x] Support setting `:nextjournal.clerk/no-cache` on namespaces as well
;; - [ ] Support setting `:nextjournal.clerk/cache-dir` on namespace
;; - [ ] Allow to control viewer expansion state programmatically
;; - [ ] Datafy + Nav, Metadata viewer via `:transform-fn`
;; - [ ] Persist viewer expansion state across reloads
;; - [ ] Improve feedback when viewer errors
;; - [ ] Make stacktraces clickable
;; - [ ] Build static build on CI
;; - [ ] Hook up new table viewer
;; - [ ] Add CAS storage option for static builds
;; ## 💡 Ideas
;; - [ ] Hook up distributed caching using CAS + cloud bucket
;; - [ ] Status log
