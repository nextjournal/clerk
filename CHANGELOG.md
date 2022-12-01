# Changelog

Changes can be:
* 🌟⭐️💫 features
* 🐞🐜 friendly or nasty bugs
* 🛠 dev improvements

## Unreleased

* 🌟 Clerk sync for vars holding atoms (#253, #268)

    Introduce `^:nextjournal.clerk/sync` metadata annotation for vars
    holding atoms to enable automatically syncing state between JVM
    Clojure and the SCI environment running in the browser: the var
    will be interned by Clerk in the sci environment and calls to
    `swap!` or `reset!` will be syncronized to the JVM. Use editscript
    for sending a minimal patch to the browser to enable 60fps
    updates.
    
* 🌟 Improvements to Clerk's SCI Environment running in the Browser

    * Simplify writing of viewer `:render-fn`s by dropping need
      `v/html` is no longer needed in render functions of custom
      viewers, vector values will be handled as reagent components.
    * Introduce `nextjournal.clerk.render` namespace to hold all
     `:render-fn`s and reference them using fully qualifed names from
     `nextjournal.clerk.viewer` to make it obvious where they are
     coming from. Also refactor `nextjournal.clerk.sci-viewer` to
     `nextjournal.clerk.sci-env`.
    * Support alias resolution for `:render-fn`s (#276)
    * Upgrade to React 18.2 and introduce
      `nextjournal.clerk.render.hoooks` as a thin cljs wrapper around
      [React hooks](https://reactjs.org/docs/hooks-intro.html) also
      useable from the sci env. (#237, #242)
    * Introduce `nextjournal.clerk.render.code` ns with support for
      read-only and editable code cells (#285)
    * Improve error handling with `ErrorBoundary` rewrite using
      `shadow.cljs.modern/defclass` (#255)
    * Fix page jump between updates for Vega and Plotly viewer and
      improve error display. This is implemented using React
      Hooks. (#231)
    * Support callback for vega viewer to access the vega-embed object
      (#279)
    * Move sci env `deps.edn` to separate deps root (#278). This allows
      folks to take over the cljs build of clerk in order to support
      additional namespaces.

* 💫 Show shape of data using auto-expansion of results (opt-in for now) (#258)

    This allows letting Clerk auto expand data results via the
    `:nextjournal.clerk/auto-expand-results? true` setting in the
    `ns` metadata. You can use the same key in `::clerk/opts` on single result
    too.
  
* 💫 Improvement to static `nextjournal.clerk/build!`

    * Allow to set Open Graph Metadata for notebooks using
      `:nextjournal.clerk/open-graph` map in ns metadata with `:url`,
      `:title`, `:description` and `:image` keys (#243)
    * Support `:ssr` setting for server-side rendering in static builds (#254, #275)
    * Support `:compile-css` attribute to compile step with Tailwind (#246)

* 🌟 Support Viewer CSS class customizations (#294)

    This supports providing custom classes to viewers and the notebook
    viewer which should allow for most use cases and does not require
    actually overriding the base styles. Once a
    `:nextjournal.clerk/css-class` is available on the viewer or in
    document settings, the available class will be used and no further
    viewer classes will be assigned.

* 💫 Let viewer opt out of var-from-def unwrapping

    This fixes an inconsistency in the viewer api: until now we'd unwrap
    a `:nextjournal.clerk/var-from-def` when a viewer is applied using an
    `fn?` like `clerk/table` but not when given a viewer map.
    
    We now always unwrap the var unless the viewer opts out with a truthy
    `:var-from-def?` key.

* 💫 Make `nextjournal.clerk.parser` usable in CLJS

* 💫 Set #-fragment when clicking on TOC items (works in unbundled
  case)
  
* 🛠 Use `sci.ctx-store` and bump sci (#282)

* 🐜 Detect interned vars to not consider them as missing, introduce
  setting to opt-out of throwing when missing vars are detected
  (#301). Fixing #247. 

* 🐜 Fix circular dep error referencing fully-qualified var (#289)

* 🐞 Fixes behaviour of `clerk/doc-url` in static app (#284)

* 🐞 Fix links to clerk-demo build (#252)

* 🐞 Bump sci with cljs.core/array (#250)

* 🐞 Fix content-addressing of image-blobs and compiled CSS during
  static build (#259)
  
* 🐞 Add validation for `:nextjournal.clerk/width` fixing #217.

* 🐞 Fix inspect with `nil` values (#263)

## 0.11.603 (2022-10-17)

* 🌟 Add 🚰 **Tap Inspector** notebook to let Clerk show `clojure.core/tap>`
  stream. Viewable via `(nextjournal.clerk/show! 'nextjournal.clerk.tap)`.

* 🌟 Improvements to static building including Clerk-viewer based
  build progress reporter: Add `nextjournal.clerk/build!` and document it, it supersedes the
  now deprecated `nextjournal.clerk/build-static-app!`

    * Support `:index` option for overriding the index filename
    * Support passing `:bundle` and `:browse` without `?`, making
      cli-usage more convienient
    * Add `:dashboard` option to show a Clerk viewer based build
      report dashboard
    * Change `:bundle` default to `false`
    * Split `:paths` into `:paths` and `:paths-fn` option to make symbol
    case explicit
    * Improve errors when passing invalid options
    * Print cli usage help when `:help` is set

* ⭐️ Extend `nextjournal.clerk/show!` accept more argument types:

    * Symbols representing namespaces on the classath:
      `(nextjournal.clerk/show! 'nextjournal.clerk.tap)`
    * Namespaces: `(nextjournal.clerk/show! (find-ns 'nextjournal.clerk.tap))`
    * URLs as strings or `java.net.URLs`: `(show! "https://raw.githubusercontent.com/nextjournal/clerk-demo/main/notebooks/rule_30.clj")`
    * In memory string readers: `(show! (java.io.StringReader. ";; # String Notebook 👋\n(+ 41 1)"))`, fixes #168
    * Everything that `clojure.core/slurp` supports

* ⭐️ Support `babashka.cli` for `nextjournal.clerk/serve!` and
  `nextjournal.clerk/build!` via metadata annoatations. To use it add
  `org.babashka/cli {:mvn/version "0.5.40"}` or newer to your `:deps`
  and set `:main-opts ["-m" "babashka.cli.exec"]`.
  
* 💫 Support providing embed options to vega `vl` viewer, can be passed via
  `:embed/opts` keys
  
* 💫 Inline plotly and vega viewers (they were previously imported
  from nextjournal/viewers) and improve error display for them
  
* 💫 Handle cljc files in analyzer/ns->file

* 🐜 Fix results with `*print-length/depth*` being set (thanks
  @russmatney, #224)

* 🐜 Fix display of nested `clojure.lang.IDeref`s (e.g. atoms).

* 🐜 Fix analyzer issues with clojure proxy (🙏 @zampino, fixes #222)

* 🐞 Fix extra wrapping in `clerk/defcached` and `clerk/with-cache`

* 🛠 Improve clerk-show emacs command (🙏 @benjamin-asdf, fixes
  #170)

* 🛠 Upgrade depdendencies, fixing warnings under Clojure 1.11.
    * `babashka/fs`: `0.1.5` → `0.1.11`
    * `babashka/sci`: `0.3.5` → `0.4.33` (🙏 @borkdude)
    * `com.taoensso/nippy`: `3.1.1` → `3.2.0`
    * `edamame`: `0.0.11` → `1.0.0`
    * `http-kit`: `2.5.3` → `2.6.0`
    * `rewrite-clj`: `1.0.699-alpha` → `1.1.45`
    * `labdaisland/uri`: `1.11.86` → `1.13.95`
    * `org.clojure/tools.analyzer`: `1.0.0` `1.1.0`

## 0.10.562 (2022-09-18)

* 🐞 Revert "Inline vega lite viewer and make it show errors" as it
  caused a regression that a vega visualization would only update
  after a refresh.

## 0.10.560 (2022-09-14)

* 💫 Improve errors on eval by keeping location information on form
* 🐜 Don't throw when valuehash cannot be computed for deref dep
* 🐞 Inline vega lite viewer and make it show errors
* 🐞 Fix multi-expand via Option+Click
* 🛠 Allow to run ui tests against local build

## 0.10.550 (2022-09-06)

* 🌟 Support setting visibility for results 🙈

    Specifying code cell & result visibility is now easier & more
    powerful. Previously setting result visibility only possible
    per-form using a `hide-result` viewer. You can now pass a map with
    `:code` and `:result` keys to control code & result visibility
    individually.

    To set this per-document, put the visibility on the ns form like
    for example:

      (ns my-namespace
        "This namespace will have code cells folded and results hidden"
        {:nextjournal.clerk/visibility {:code :fold :result :hide}})

    You can still override this per-form using metadata, so the
    following form will be shown.
    
      ^{:nextjournal.clerk/visibility {:code :show :result :show}}
      (inc 41)
    
    or change the defaults midway through the doc using a _visibility
    marker_:
    
      {:nextjournal.clerk/visibility {:code :show :result :show}}
            
    Also support `:nextjournal.clerk/toc` setting on ns metadata.
    
* ⭐️ Fail eval if var is only present at runtime but not in file 🕵🏻
    
    This makes Clerk be more strict than the REPL and actually complain
    when one still depends on a var no longer present in the file.
    
    The check is currently only performed for the notebook being shown iff
    it starts with an `ns` form.
    
    
* 💫 Rename viewer attribute `[:fetch-opts :n]` to `:page-size`
* 💫 More subtle indication for folded code cells

* 💫 Cut down of depedencies of `nextjournal.clerk.sci-viewer` in
  order to simplify consumption as a library and slim down bundle by
  290kb (73kb gzip)

* 💫 Unbundle images when `:bundle?` is `false` (#208)

    As a quick fix to make the Clerk Book viewable we're now writing
    images for the static build to files when `:bundle?` is set to
    false. In a follow-up we'll support absolute urls for the images
    and introduce a separate flag for this.

* 🐜 Don't attempt to check bounded count limit for non-freezable
  things, fixes #199 (#201)
* 🐜 Fix regression in showing sorted-map results
* 🐜 Fix table viewer normalization error when given sorted map
* 🐞 Use PngEncoder lib for 10x improvement in encoding performance (#197)
* 🐞 Overflow per single result not by result container (#198)
    
    When result contains multiple tables, allow scrolling each table
    individually instead of the entire result container. Also works
    with plots.

* 🐞 Equalizes vertical spacing between Markdown, code and results
* 🐞 Fixes the quoted string viewer layout when expanded

## 0.9.513 (2022-07-18)

* 🌟 Valuehash ⚛️

    Treat `clojure.core/deref` expressions separately in the dependency graph
    and attempt to compute a hash at runtime based on the value of the
    expression. This lets Clerk see an updated value for these expressions
    without needing to opt out of Clerk's caching using `^:nextjournal.clerk/no-cache` (#187).

* ⭐️ Expand indicators & allow option-click to expand all siblings

    This adds an affordance to make it obvious that collections can be expanded. In
    addition, we support Option-Click to expand all sibling nodes on a level.

* ⭐️ Add `nextjournal.clerk/eval-cljs-str` that can be used to provide
  viewer `:render-fn`s from other sources. Defining them in a separate
  .cljs file makes linting work on them.

* 💫 Add docstrings for Clerk's public API in `nextjournal.clerk`.

* 🐞 Improve error handling in `:render-fn`s, showing better errors for
  different failure modes:
  
  * undefined symbol on read
  * runtime exception
  * calling `inspect` without a valid presented value
  * passing an invalid hiccup form to `v/html`

* 🐞 Fix Clerk's dependency analysis to detect in macros
* 🛠 Bump deps of `io.github.nextjournal/clojure-mode` and `io.github.nextjournal/markdown`
* 🛠 Refactor `builder`, `parser` `eval`, to its own namespaces, rename `hashing` to `analysis`.
* 🛠 Remove keyword indirection from viewer API
* 🛠 Lean more heavily on `tools.analyzer` for depedency analysis

## 0.8.470 (2022-06-20)
* 🐞 Markdown library now uses a GraalJS version compatible with Java 8. Fixes #178
* 🐞 Bundle asset map during Maven release to allow clerk to function behind a proxy. Fixes #147
* 🛠 Preserve asset name in content-addressed asset URL (#181)

## 0.8.463 (2022-06-16)
* 💫 Support `:nextjournal.clerk/no-cache` meta on form also for vars.

    Previously it would have to go on a different place for vars than
    for unnamed top-level expressions.

    Setting it on the var will be deprecated and so consistently
    setting it on the form is recommend from now on.

* 💫 Support rich values (with viewers) in table headers
* 🐜 Preserve `*ns*` during analysis and eval (#173)
* 🐜 Upgrade markdown library with
    * support for loose lists
    * fix for rendering of inline images
    * updated dependencies for `clojure.data.json` and `org.graalvm.js/js`
* 🐞 Reduce margin between prose and results
* 🐜 Fix regression in parsing markdown blocks following code cells in
  wrong order
* 🛠 Switch to kaocha for better test report output


## 0.8.451 (2022-06-09)
* ⭐ Move default viewers to vars to make inspecting & building on them easier (#167)
* 💫 Introduce flexible grid layout viewers `row` and `col` (#162)
* 🐜 Introduce checks for forms which cannot be cached to disk (#166)
* 🐞 Display inline comments in code cell rather than in prose, fixes #71.

## 0.8.445 (2022-06-01)
* 💫 First cut of Clerk Examples

    Add the `nextjournal.clerk/example` macro that evaluates to `nil`,
    just like `clojure.core/comment` when used outside of Clerk. When
    used in the context of Clerk it renders the expressions with their
    resulting values.
    
* 🐞 Fix reported duration for static bundle build step

## 0.8.442 (2022-06-01)
* 🌟 Simplify viewer api by letting `:transform-fn` act on wrapped-value (#152)
    
    This simplifies the viewer api by letting `:transform-fn` act on
    the wrapped-value. This way the `:transform-fn` can now serve as the
    single JVM-extension point and also serve as `:fetch-fn` (using
    `mark-presented`) and `:update-viewers-fn`.
    
    In the case of a `:fetch-fn` the transformation would previously
    happen in a second pass. Now it is always eager, which should make it
    much clearer what's happening.
    
    Also do a naming pass:
    
    * `describe` → `present`
    * `merge-descriptions` → `merge-presentations`
    
    
* ⭐️ Extend the reach of the viewer api to the root node.

    This allows full customization of how a Clerk doc is displayed. Showcase
    that by implementing a slideshow viewer.
  
* ⭐️ Show render-fn errors and simplify and improve default viewers

    Show a somewhat useful error when a viewer's `:render-fn` errors, either on eval or when invoked as a render function later. Since the stack property of `js/Error` isn't standardized the usefulness differs between browsers and variants. The advanced compiled js bundle is currently lacking source maps so the stack isn't useful there, yet.

    Also simplify and improve the default viewers by having a fallback reader function for unknown tags and display anything that's readable with it using the pagination inside the browser. We can use this as a generic fallback and drop a number of specialised viewers (`uuid?`, `fn?`, `inst?`). It also means Clerk will now display these fallback objects identical to how Clojure will print them. Note that this also means that bringing in library like cider.nrepl that extends `print-method` will affect how things are displayed in Clerk.

    Lastly, we include a viewer for `clojure.lang.IDeref` that will use Clerk's JVM-side pagination behaviour for the `:val`.

    More rationale and live examples in the [Better Printing ADR](https://snapshots.nextjournal.com/clerk/build/7f510cde367ee9de6765c3f3dd7013e8bf19c64b/index.html#/notebooks/viewers/printing.clj) notebook.

* 💫 Refactor analysis to support multiple defs per top-level form (#159)
* 🐜 Make no-cache on side-effecting var invalidate dependents (#158), fixes #157
* 🐞 Fix lazy loading when viewer is selected via metadata.
* 🐞 Perform bounded count limit check on tree (#154)

    Previously this would only be performed on the root node so we'd go out of
    memory attempting to cache a value like `{:a (range)}`.
    
* 🛠 Update SCI & SCI configs (#151)
* 🛠 Start Clerk on `bb dev` after first cljs compile and forward serve opts.

## 0.7.418 (2022-04-20)
* 🐜 Fix regression in heading sizes & margins (#135)

## 0.7.416 (2022-04-19)
* 🌟 Support Table of Contents & dark mode toggle, #109. ToC is opt-in via `^:nextjournal.clerk/toc` metadata on ns form.
* 🌟 Use viewer api to to enable full customization of markdown nodes (#122)
* 💫 Expand glob paths and support symbols pointing to vars for `build-static-app!` (#130)
* 💫 Use relative links inside the static-app (#132)
* 🐞 Always open static build index via file: protocol, closes #96
* 🐞 Remove column truncation in table viewer (#124)
* 🐞 Make lazy loading respect viewers on form
* 🐞 Fix .cljc file renaming in unbundled static app (#123)
* 🐞 Leave it to viewers to opt into tailwind's `not-prose` class
* 🛠 First cut of browser tests using nbb & playwright (#97)
* 🛠 Write hash in pre-commit hook for predicable viewer.js location, build JS in CI (#107)
  
## 0.6.387 (2022-03-03)
* 🌟 Add `clerk/recompute!` for fast recomputation of doc without re-parsing & analysis
* 🌟 Normalize viewers to support full map form (#77)
* 🌟 Less whitespace and better alignment when expanding nested maps
* ⭐️ Add reagent and js-interop to viewer api (#105)
* ⭐️ Add `with-d3-require` to viewer api, tweak sci read opts (#86)
* 💫 Make string viewer show newlines and allow to toggle breaking (#104)
* 💫 Tweaked the theme and make data & code viewers use consistent color scheme. Prepare dark mode support.
* 💫 Add experimental `defcached` and `with-cache` macros to enable access to Clerk's view of a var or expression respectively.
* 💫 Let code viewers use horizontal scrolling instead of line  wrapping to improve readability, especially on mobile.
* 🐞 Make `clear-cache!` also clear in memory cache (#100)
* 🐞 Protect var-from-def? against types that throw on get, closes #64
* 🐞 Drop unused tools.deps.alpha dep
* 🐜 Fix inconsistent hashing when form contains a regex (#85)
* 🐜 Fix find-location for namespaces with dashes in them
* 🐜 Fix in memory cache not being used for unfreezable results (#82)
* 🐜 Don't catch errors occurring on JVM-side of the viewer api (`:pred` & `:transform-fn`)
* 🐜 Opt out of caching deref forms
* 🐜 Protect cache from caching nil from repeated defonce eval
* 💫 Upgrade to SCI v0.3.1

## 0.5.346 (2022-01-27)
This release focuses on improving the viewer api:

* 🌟 new built-in image viewer component  for `java.awt.image.BufferedImage` with automatic layouting. These can be easily created constructed from `javax.imageio.ImageIO/read` from `File`, `URL` or `InputStream`.
* 🌟 Enable nested viewers inside e.g. `html` or `table` viewers.
* 🌟 Allow to convey viewers out-of-band using metadata. Clerk's viewer api has been based on functions. This can be undesired if you want to depend on the unmodified value downstream. You can now alternatively use metadata using the `:nextjournal.clerk/viewer` to convey the viewer. Valid values are viewer functions or keywords. The latter is useful when you don't want a runtime dependency on Clerk. (#58)
* 💫 `:render-fn` must now be quoted to make it clearer it doesn't run on the JVM but in the browser (#53)
* 💫 Make all viewer functions take an optional map to specify the with using the `:nextjournal.clerk/width` with valid values `:full`, `:wide` or `:prose`. (#53)
* 💫 Enable access to vars resulting from eval in viewers @philomates (#47)
* 💫 Expose title, table of contents as the result of parse. Set title in browser. @zampino (#56)
* 💫 Add halt! to allow stopping Clerk @SneakyPeet (#43)*
* 💫 Upgrade to tailwindcss 3 and use via play cdn. This enables using any tailwind properties. (#36)
* 💫 Allow to bring your own css and js (#55)
* 🐜 Introduce print budget to elide deeply nested structures. This should fix overflowing the browser with too much data for certain shapes of data (#48)
* 🐞 Recover from a viewer rendering error without requiring a browser reload and improve error display.
* 🛠 Refactor & tests various parts

## 0.4.316 (2021-12-21)
* 💫 Add option to control opening of built static app (@filipesilva, #31)
* 🐜 Fix path error on windows by bumping markdown dep (#34)
* 🐞 Fix browse in `build-static-app!` on windows (#39)

## 0.4.305 (2021-12-13)
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
