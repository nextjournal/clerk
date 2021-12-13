# Changelog

Changes can be:
* 🌟⭐️💫 features
* 🐞🐜 friendly or nasty bugs
* 🛠 dev improvements

## Unreleased
* 🌟 Support markdown as an alternative to Clojure source code for prose-heavy documents.
* 🌟 Changed viewer api: Added a `:transform-fn` that allows a transformation of a value in JVM Clojure. Also rename `:fn` to `:render-fn` for clarify and ensure the `:pred` only runs on JVM Clojure to enable using predicates that cannot run in sci in the browser. Add support for serving arbitrary blobs  via a `:fetch-fn` that returns a map with `:nextjournal/content-type` and `nextjournal/value` keys ([example](https://github.com/nextjournal/clerk/blob/8ad88630f746f1a9ff3ac314f5528c2d25c42583/notebooks/viewers/image.clj)).
* 🌟 Added `v/clerk-eval` to the viewer api which takes a quoted form and evaluates it in the context of the document namespace.
* ⭐️ Allow setting code cell & result visibility via
  `:nextjournal.clerk/visibility` metadata on a form. Valid values are: `:show` (default) `:fold` or `:hide`. These settings can be set on individual forms, setting them on the `ns` form changes the default for all forms in the document. If you want to affect only the `ns` form, use  `:fold-ns` or `:hide-ns` ([example](https://github.com/nextjournal/clerk/blob/8ad88630f746f1a9ff3ac314f5528c2d25c42583/notebooks/visibility.clj)).
* 💫 Added a dynamic binding `config/*in-clerk*` that is only true when Clerk is driving evaluation.
* 💫 Persist viewer expansion state across document reloads and make document updates minimize repaints and flickering.
* 💫 Keep document scroll position when an error is shown by moving error into overlay.
* 💫 Wrap sci context in an atom to let consumers change it.
* 💫 Exclude Emacs lock files from being shown via file watcher #22 (@ikappaki)
* 💫 Add viewers for Clojure vars and a catch all `pr-str` viewer
* 🐜 Don't cache uncountable values within `*bounded-count-limit*` #15.
* 🐞 Prevent infinite loop when calling `show!` in a notebook using `config/*in-clerk*`.

## 0.3.233 (2021-11-10)

* 💫 Bump viewers & deps to support ordered list offsets
* 🐜 Fix error in describe when sorting sets of maps
* 🐜 Fix arity exception when running table viewer predicates
* 🐞 Restore page size of 20 for seqs
* 🐞 Fix regression in closing parens assignment
* 🛠 Automate github releases


## 0.3.220 (2021-11-09)

* 🌟 Add much improved table viewer supporting lazy loading and data normalization
* 💫 Add char viewer
* 🐞 Fix exception when rendering hiccup containing seqs
* 🐞 Parse set literals as top level expressions
* 🐞 Add `scm` information to `pom.xml`

## 0.2.214 (2021-11-04)

* 💫 Support setting `:nextjournal.clerk/no-cache` on namespaces
* 🐜 Fix for unbound top-level forms not picking up dependency changes

## 0.2.209 (2021-11-03)

* 🌟 Enable lazy loading for description and combine with `fetch`. This let's Clerk handle moderately sized datasets without breaking a sweat.
* ⭐️ Add `clerk/serve!` function as main entry point, this allows pinning of notebooks
* 💫 Compute closing parens instead of trying to layout them in CSS
* 💫 Fix and indicate viewer expansion
* 🐜 Wrap inline results in edn to fix unreadable results in static build
* 🐜 Make hiccup viewer compatible with reagent 1.0 or newer
* 🐞 Fix lazy loading issues arising from inconsistent sorting
* 🐞 Remove `->table` hack and use default viewers for sql
* 🐞 Fix check in `maybe->fn+form` to ensure it doesn't happen twice
* 🐞 Defend `->edn` from non clojure-walkable structures
* 🐞 Fix error when describing from non-seqable but countable objects
* 🐞 Fix error in `build-static-html!` when out dirs don't exist
* 🛠 Setup static snapshot builds
* 🛠 Fix stacktraces in dev using -OmitStackTraceInFastThrow jvm opt
* 🛠 Add build task for uploading assets to CAS and update in code


## 0.1.179 (2021-10-18)

* 🐞 Fix lazy loading for non-root elements
* 🐞 Fix exception when lazy loading end of string
* 🐞 Fix regression in `clerk/clear-cache!`

## 0.1.176 (2021-10-12)

💫 Clerk now runs on Windows.

* 🐜 Fix error showing a notebook on Windows by switching from datoteka.fs to babashka.fs
* 🐞 Fix parsing issue on Windows by bumping `rewrite-clj`, see clj-commons/rewrite-clj#93

## 0.1.174 (2021-10-10)

* ⭐️ Support macros that expand to requires
* 💫 Better function viewer showing name
* 💫 Add viewport meta tag to fix layout on mobile devices
* 🐜 Fix error in returning results when unsorted types (e.g. maps & sets) contain inhomogeneous keys.
* 🐜 Add reader for object tag
* 🐞 Fix view when result is a function
* 🐞 Fix display of false results
* 🐞 Fix map entry display for deeply nested maps
* Show plain edn for unreadable results
* 🐞 Fix showing maps & sets with inhomogeneous types
* 🛠 Fix live reload in dev by using different DOM id for static build

## 0.1.164 (2021-10-08)

👋 First numbered release.
