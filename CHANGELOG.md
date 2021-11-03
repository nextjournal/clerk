# Changelog

## Unreleased

* Bump viewers
* Remove `->table` hack and use default viewers for sql
* Setup static snapshot builds
* Enable lazy loading for description and combine with `fetch`
* Compute closing parens
* Fix check in `maybe->fn+form` to ensure it doesn't happen twice
* Add build task for uploading assets to CAS and update in code
* Fix lazy loading issues arising from inconsistent sorting
* Fix and indicate viewer expansion
* Wrap inline results in edn to fix unreadable results in static build
* Defend ->edn from non clojure-walkable structures
* Fix error when describing from non-seqable but countable objects
* Fix stacktraces in dev using -OmitStackTraceInFastThrow jvm opt
* Fix error in `build-static-html!` when out dirs don't exist
* Add `clerk/serve!` function as main entrypoint, this allows pinning
  of notebooks
* Make hiccup viewer compatible with reagent 1.0 or newer

## 0.1.179 (2021-10-18)

* Fix lazy loading for non-root elements
* Fix exception when lazy loading end of string
* Fix regression in clerk/clear-cache!

## 0.1.176 (2021-10-12)

* Replace datoteka/fs with babashka/fs for windows compatability
* Bump rewrite-clj for windows compatability, see clj-commons/rewrite-clj#93

## 0.1.174 (2021-10-10)

* Support macros that expand to requires
* Better function viewer showing name
* Fix display of false results
* Fix view when result is a function
* Add reader for object tag
* Fix live reload in dev by using different dom id for static build
* Fix map entry display for deeply nested maps
* Show plain edn for unreadable results
* Fix showing maps & sets with inhomogeneous types
* Implement a resilient sorting that falls back to ranking according
  to default viewer predicates.
* Add viewport meta tag to fix layout on mobile devices

## 0.1.164 (2021-10-08)

First numbered release.
