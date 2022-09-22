# Table of contents
-  [`nextjournal.clerk`](#nextjournalclerk)  - Clerk's Public API.
    -  [`->value`](#->value) - Takes <code>x</code> and returns the <code>:nextjournal/value</code> from it, or otherwise <code>x</code> unmodified.
    -  [`add-viewers`](#add-viewers)
    -  [`add-viewers!`](#add-viewers-1) - Adds <code>viewers</code> to the viewers associated with the current <code>*ns*</code>.
    -  [`build-static-app!`](#build-static-app)
    -  [`clear-cache!`](#clear-cache) - Clears the in-memory and file-system caches.
    -  [`code`](#code) - Displays <code>x</code> as syntax highlighted Clojure code.
    -  [`col`](#col) - Displays <code>xs</code> as columns.
    -  [`defcached`](#defcached) - Like <code>clojure.core/def</code> but with Clerk's caching of the value.
    -  [`doc-url`](#doc-url)
    -  [`eval-cljs-str`](#eval-cljs-str) - Evaluates the given ClojureScript <code>code-string</code> in the browser.
    -  [`example`](#example) - Evaluates the expressions in <code>body</code> showing code next to results in Clerk.
    -  [`file->viewer`](#file->viewer) - Evaluates the given <code>file</code> and returns it's viewer representation.
    -  [`get-default-viewers`](#get-default-viewers) - Gets Clerk's default viewers.
    -  [`halt!`](#halt) - Stops the Clerk webserver and file watcher.
    -  [`halt-watcher!`](#halt-watcher) - Halts the filesystem watcher when active.
    -  [`hide-result`](#hide-result) - Deprecated, please put ^{:nextjournal.clerk/visibility {:result :hide}} metadata on the form instead.
    -  [`html`](#html) - Displays <code>x</code> using the html-viewer.
    -  [`mark-presented`](#mark-presented) - Marks the given <code>wrapped-value</code> so that it will be passed unmodified to the browser.
    -  [`mark-preserve-keys`](#mark-preserve-keys) - Marks the given <code>wrapped-value</code> so that the keys will be passed unmodified to the browser.
    -  [`md`](#md) - Displays <code>x</code> with the markdown viewer.
    -  [`notebook`](#notebook) - Experimental notebook viewer.
    -  [`plotly`](#plotly) - Displays <code>x</code> with the plotly viewer.
    -  [`recompute!`](#recompute) - Recomputes the currently visible doc, without parsing it.
    -  [`reset-viewers!`](#reset-viewers) - Resets the viewers associated with the current <code>*ns*</code> to <code>viewers</code>.
    -  [`row`](#row) - Displays <code>xs</code> as rows.
    -  [`serve!`](#serve) - Main entrypoint to Clerk taking an configurations map.
    -  [`set-viewers!`](#set-viewers) - Deprecated, please use <code>add-viewers!</code> instead.
    -  [`show!`](#show) - Evaluates the Clojure source in <code>file</code> and makes Clerk show it in the browser.
    -  [`table`](#table) - Displays <code>xs</code> using the table viewer.
    -  [`tex`](#tex) - Displays <code>x</code> as LaTeX using KaTeX.
    -  [`update-val`](#update-val) - Take a function <code>f</code> and optional <code>args</code> and returns a function to update only the <code>:nextjournal/value</code> inside a wrapped-value.
    -  [`update-viewers`](#update-viewers) - Takes <code>viewers</code> and a <code>select-fn->update-fn</code> map returning updated viewers with each viewer matching <code>select-fn</code> will by updated using the function in <code>update-fn</code>.
    -  [`use-headers`](#use-headers) - Treats the first element of the seq <code>xs</code> as a header for the table.
    -  [`valuehash`](#valuehash)
    -  [`vl`](#vl) - Displays <code>x</code> with the vega embed viewer, supporting both vega-lite and vega.
    -  [`with-cache`](#with-cache) - An expression evaluated with Clerk's caching.
    -  [`with-viewer`](#with-viewer) - Displays <code>x</code> using the given <code>viewer</code>.
    -  [`with-viewers`](#with-viewers)
-  [`nextjournal.clerk.builder`](#nextjournalclerkbuilder)  - Clerk's Static App Builder.
    -  [`->html-extension`](#->html-extension)
    -  [`build-static-app!`](#build-static-app-1)
    -  [`clerk-docs`](#clerk-docs)
    -  [`expand-paths`](#expand-paths)
    -  [`process-build-opts`](#process-build-opts)
    -  [`stdout-reporter`](#stdout-reporter)
    -  [`strip-index`](#strip-index)
    -  [`write-static-app!`](#write-static-app) - Creates a static html app of the seq of <code>docs</code>.
-  [`nextjournal.clerk.classpath`](#nextjournalclerkclasspath) 
    -  [`classpath-directories`](#classpath-directories) - Like <code>clojure.java.classpath/classpath-directories</code> but using the <code>system-classpath</code> which cider doesn't break, see https://github.com/clojure-emacs/cider-nrepl/pull/668.
    -  [`classpath-jarfiles`](#classpath-jarfiles) - Like <code>clojure.java.classpath/classpath-jarfiles</code> but using the <code>system-classpath</code> which cider doesn't break, see https://github.com/clojure-emacs/cider-nrepl/pull/668.
-  [`nextjournal.clerk.config`](#nextjournalclerkconfig) 
    -  [`!asset-map`](#asset-map)
    -  [`!resource->url`](#resource->url)
    -  [`*bounded-count-limit*`](#bounded-count-limit)
    -  [`*in-clerk*`](#in-clerk)
    -  [`cache-dir`](#cache-dir)
    -  [`cache-disabled?`](#cache-disabled?)
    -  [`gs-url-prefix`](#gs-url-prefix)
    -  [`lookup-hash`](#lookup-hash)
    -  [`lookup-url`](#lookup-url)
    -  [`resource-manifest-from-props`](#resource-manifest-from-props)
-  [`nextjournal.clerk.eval`](#nextjournalclerkeval)  - Clerk's incremental evaluation with in-memory and disk-persisted caching layers.
    -  [`+eval-results`](#+eval-results) - Evaluates the given <code>parsed-doc</code> using the <code>in-memory-cache</code> and augments it with the results.
    -  [`->cache-file`](#->cache-file)
    -  [`elapsed-ms`](#elapsed-ms)
    -  [`eval-analyzed-doc`](#eval-analyzed-doc)
    -  [`eval-doc`](#eval-doc) - Evaluates the given <code>doc</code>.
    -  [`eval-file`](#eval-file) - Reads given <code>file</code> (using <code>slurp</code>) and evaluates it.
    -  [`eval-string`](#eval-string) - Evaluated the given <code>code-string</code> using the optional <code>in-memory-cache</code> map.
    -  [`hash+store-in-cas!`](#hash+store-in-cas)
    -  [`maybe-eval-viewers`](#maybe-eval-viewers)
    -  [`read+eval-cached`](#read+eval-cached)
    -  [`thaw-from-cas`](#thaw-from-cas)
    -  [`time-ms`](#time-ms) - Pure version of <code>clojure.core/time</code>.
    -  [`wrapped-with-metadata`](#wrapped-with-metadata)
-  [`nextjournal.clerk.graph-visualizer`](#nextjournalclerkgraph-visualizer) 
    -  [`show-graph`](#show-graph)
    -  [`viewer`](#viewer)
-  [`nextjournal.clerk.parser`](#nextjournalclerkparser)  - Clerk's Parser turns Clojure & Markdown files and strings into Clerk documents.
    -  [`->doc-settings`](#->doc-settings)
    -  [`->doc-visibility`](#->doc-visibility)
    -  [`->visibility`](#->visibility)
    -  [`add-block-visibility`](#add-block-visibility)
    -  [`auto-resolves`](#auto-resolves)
    -  [`code-cell?`](#code-cell?)
    -  [`code-tags`](#code-tags)
    -  [`ns?`](#ns?)
    -  [`parse-clojure-string`](#parse-clojure-string)
    -  [`parse-file`](#parse-file)
    -  [`parse-markdown-cell`](#parse-markdown-cell)
    -  [`parse-markdown-string`](#parse-markdown-string)
    -  [`parse-visibility`](#parse-visibility)
    -  [`read-string`](#read-string)
    -  [`remove-leading-semicolons`](#remove-leading-semicolons)
    -  [`visibility-marker?`](#visibility-marker?)
    -  [`whitespace-on-line-tags`](#whitespace-on-line-tags)
-  [`nextjournal.clerk.sci-viewer`](#nextjournalclerksci-viewer) 
    -  [`!doc`](#doc)
    -  [`!edamame-opts`](#edamame-opts)
    -  [`!error`](#error)
    -  [`!eval-counter`](#eval-counter)
    -  [`!sci-ctx`](#sci-ctx)
    -  [`!viewers`](#viewers)
    -  [`check-icon`](#check-icon)
    -  [`clerk-eval`](#clerk-eval)
    -  [`code-viewer`](#code-viewer)
    -  [`coll-view`](#coll-view)
    -  [`coll-viewer`](#coll-viewer)
    -  [`color-classes`](#color-classes)
    -  [`dark-mode-toggle`](#dark-mode-toggle)
    -  [`default-viewers`](#default-viewers)
    -  [`doc-url`](#doc-url-1) - Stub implementation to be replaced during static site generation.
    -  [`elision-viewer`](#elision-viewer)
    -  [`error-badge`](#error-badge)
    -  [`error-boundary`](#error-boundary)
    -  [`error-view`](#error-view)
    -  [`eval-form`](#eval-form)
    -  [`eval-viewer-fn`](#eval-viewer-fn)
    -  [`expand-button`](#expand-button)
    -  [`expand-icon`](#expand-icon)
    -  [`expand-style`](#expand-style)
    -  [`expandable?`](#expandable?)
    -  [`fetch!`](#fetch)
    -  [`foldable-code-viewer`](#foldable-code-viewer)
    -  [`html`](#html-1)
    -  [`html-render`](#html-render)
    -  [`html-viewer`](#html-viewer)
    -  [`in-process-fetch`](#in-process-fetch)
    -  [`inspect`](#inspect)
    -  [`inspect-children`](#inspect-children)
    -  [`inspect-presented`](#inspect-presented)
    -  [`katex-viewer`](#katex-viewer)
    -  [`local-storage-dark-mode-key`](#local-storage-dark-mode-key)
    -  [`map-view`](#map-view)
    -  [`map-viewer`](#map-viewer)
    -  [`mathjax-viewer`](#mathjax-viewer)
    -  [`mount`](#mount)
    -  [`nbsp`](#nbsp)
    -  [`normalize-viewer-meta`](#normalize-viewer-meta)
    -  [`notebook`](#notebook-1)
    -  [`number-viewer`](#number-viewer)
    -  [`opts->query`](#opts->query)
    -  [`plotly-viewer`](#plotly-viewer)
    -  [`quoted-string-viewer`](#quoted-string-viewer)
    -  [`read-result`](#read-result)
    -  [`read-string`](#read-string-1)
    -  [`reagent-viewer`](#reagent-viewer)
    -  [`render-with-viewer`](#render-with-viewer)
    -  [`result-viewer`](#result-viewer)
    -  [`root`](#root)
    -  [`sci-viewer-namespace`](#sci-viewer-namespace)
    -  [`set-dark-mode!`](#set-dark-mode)
    -  [`set-state`](#set-state)
    -  [`set-viewers!`](#set-viewers-1)
    -  [`setup-dark-mode!`](#setup-dark-mode)
    -  [`sort!`](#sort)
    -  [`sort-data`](#sort-data)
    -  [`string-viewer`](#string-viewer)
    -  [`table-error`](#table-error)
    -  [`tagged-value`](#tagged-value)
    -  [`throwable-viewer`](#throwable-viewer)
    -  [`toc-items`](#toc-items)
    -  [`toggle-expanded`](#toggle-expanded)
    -  [`triangle`](#triangle)
    -  [`triangle-spacer`](#triangle-spacer)
    -  [`unreadable-edn-viewer`](#unreadable-edn-viewer)
    -  [`url-for`](#url-for)
    -  [`valid-react-element?`](#valid-react-element?)
    -  [`vega-lite-viewer`](#vega-lite-viewer)
    -  [`x-icon`](#x-icon)
-  [`nextjournal.clerk.static-app`](#nextjournalclerkstatic-app) 
    -  [`!match`](#match)
    -  [`!state`](#state)
    -  [`doc-url`](#doc-url-2)
    -  [`get-routes`](#get-routes)
    -  [`hiccup`](#hiccup)
    -  [`index`](#index)
    -  [`init`](#init)
    -  [`mount`](#mount-1)
    -  [`root`](#root-1)
    -  [`show`](#show-1)
    -  [`ssr`](#ssr)
-  [`nextjournal.clerk.view`](#nextjournalclerkview) 
    -  [`->html`](#->html)
    -  [`->static-app`](#->static-app)
    -  [`doc->html`](#doc->html)
    -  [`doc->static-html`](#doc->static-html)
    -  [`doc->viewer`](#doc->viewer)
    -  [`include-css+js`](#include-css+js)
    -  [`include-viewer-css`](#include-viewer-css)
-  [`nextjournal.clerk.viewer`](#nextjournalclerkviewer) 
    -  [`!viewers`](#viewers-1) - atom containing a map of and per-namespace viewers or <code>:defaults</code> overridden viewers.
    -  [`->ViewerEval`](#->ViewerEval)
    -  [`->ViewerFn`](#->ViewerFn)
    -  [`->display`](#->display)
    -  [`->edn`](#->edn)
    -  [`->fetch-opts`](#->fetch-opts)
    -  [`->opts`](#->opts)
    -  [`->value`](#->value-1) - Takes <code>x</code> and returns the <code>:nextjournal/value</code> from it, or otherwise <code>x</code> unmodified.
    -  [`->viewer`](#->viewer) - Returns the <code>:nextjournal/viewer</code> for a given wrapped value <code>x</code>, <code>nil</code> otherwise.
    -  [`->viewer-eval`](#->viewer-eval)
    -  [`->viewer-fn`](#->viewer-fn)
    -  [`->viewers`](#->viewers) - Returns the <code>:nextjournal/viewers</code> for a given wrapped value <code>x</code>, <code>nil</code> otherwise.
    -  [`ViewerEval`](#ViewerEval)
    -  [`ViewerFn`](#ViewerFn)
    -  [`add-viewers`](#add-viewers-2)
    -  [`add-viewers!`](#add-viewers-3)
    -  [`apply-viewer-unwrapping-var-from-def`](#apply-viewer-unwrapping-var-from-def)
    -  [`apply-viewers`](#apply-viewers)
    -  [`apply-viewers*`](#apply-viewers-1)
    -  [`assign-closing-parens`](#assign-closing-parens)
    -  [`assign-content-lengths`](#assign-content-lengths)
    -  [`assign-expanded-at`](#assign-expanded-at)
    -  [`base64-encode-value`](#base64-encode-value)
    -  [`boolean-viewer`](#boolean-viewer)
    -  [`bounded-count-opts`](#bounded-count-opts)
    -  [`buffered-image-viewer`](#buffered-image-viewer)
    -  [`char-viewer`](#char-viewer)
    -  [`code`](#code-1)
    -  [`code-block-viewer`](#code-block-viewer)
    -  [`code-folded-viewer`](#code-folded-viewer)
    -  [`code-viewer`](#code-viewer-1)
    -  [`col`](#col-1)
    -  [`col-viewer`](#col-viewer)
    -  [`compute-expanded-at`](#compute-expanded-at)
    -  [`count-viewers`](#count-viewers) - Helper function to walk a given <code>x</code> and replace the viewers with their counts.
    -  [`datafied?`](#datafied?)
    -  [`datafy-scope`](#datafy-scope)
    -  [`default-viewers`](#default-viewers-1)
    -  [`demunge-ex-data`](#demunge-ex-data)
    -  [`desc->values`](#desc->values) - Takes a <code>description</code> and returns its value.
    -  [`doc-url`](#doc-url-3)
    -  [`drop+take-xf`](#drop+take-xf) - Takes a map with optional <code>:n</code> and <code>:offset</code> and returns a transducer that drops <code>:offset</code> and takes <code>:n</code>.
    -  [`elision-viewer`](#elision-viewer-1)
    -  [`ensure-sorted`](#ensure-sorted)
    -  [`ensure-wrapped`](#ensure-wrapped) - Ensures <code>x</code> is wrapped in a map under a <code>:nextjournal/value</code> key.
    -  [`ensure-wrapped-with-viewers`](#ensure-wrapped-with-viewers)
    -  [`eval-cljs-result-viewer`](#eval-cljs-result-viewer)
    -  [`eval-cljs-str`](#eval-cljs-str-1)
    -  [`example-viewer`](#example-viewer)
    -  [`examples-viewer`](#examples-viewer)
    -  [`fallback-viewer`](#fallback-viewer)
    -  [`fetch-all`](#fetch-all)
    -  [`find-elision`](#find-elision)
    -  [`find-named-viewer`](#find-named-viewer)
    -  [`find-viewer`](#find-viewer)
    -  [`get-default-viewers`](#get-default-viewers-1)
    -  [`get-elision`](#get-elision)
    -  [`get-fetch-opts-n`](#get-fetch-opts-n)
    -  [`get-safe`](#get-safe)
    -  [`get-viewers`](#get-viewers)
    -  [`hide-result`](#hide-result-1) - Deprecated, please put ^{:nextjournal.clerk/visibility {:result :hide}} metadata on the form instead.
    -  [`hide-result-viewer`](#hide-result-viewer)
    -  [`html`](#html-2)
    -  [`html-viewer`](#html-viewer-1)
    -  [`ideref-viewer`](#ideref-viewer)
    -  [`inherit-opts`](#inherit-opts)
    -  [`inspect-fn`](#inspect-fn)
    -  [`inspect-wrapped-value`](#inspect-wrapped-value)
    -  [`into-markup`](#into-markup)
    -  [`js-array-viewer`](#js-array-viewer)
    -  [`js-object-viewer`](#js-object-viewer)
    -  [`katex-viewer`](#katex-viewer-1)
    -  [`keyword-viewer`](#keyword-viewer)
    -  [`make-elision`](#make-elision)
    -  [`map->ViewerEval`](#map->ViewerEval)
    -  [`map->ViewerFn`](#map->ViewerFn)
    -  [`map-entry-viewer`](#map-entry-viewer)
    -  [`map-viewer`](#map-viewer-1)
    -  [`mark-presented`](#mark-presented-1)
    -  [`mark-preserve-keys`](#mark-preserve-keys-1)
    -  [`markdown-viewer`](#markdown-viewer)
    -  [`markdown-viewers`](#markdown-viewers)
    -  [`mathjax-viewer`](#mathjax-viewer-1)
    -  [`maybe-store-result-as-file`](#maybe-store-result-as-file)
    -  [`md`](#md-1)
    -  [`merge-presentations`](#merge-presentations)
    -  [`missing-pred`](#missing-pred)
    -  [`nil-viewer`](#nil-viewer)
    -  [`normalize-map-of-seq`](#normalize-map-of-seq)
    -  [`normalize-seq-of-map`](#normalize-seq-of-map)
    -  [`normalize-seq-of-seq`](#normalize-seq-of-seq)
    -  [`normalize-seq-to-vec`](#normalize-seq-to-vec)
    -  [`normalize-table-data`](#normalize-table-data)
    -  [`normalize-viewer`](#normalize-viewer)
    -  [`normalize-viewer-opts`](#normalize-viewer-opts)
    -  [`notebook`](#notebook-2)
    -  [`notebook-viewer`](#notebook-viewer)
    -  [`number-hex-viewer`](#number-hex-viewer)
    -  [`number-viewer`](#number-viewer-1)
    -  [`path-to-value`](#path-to-value)
    -  [`plotly`](#plotly-1)
    -  [`plotly-viewer`](#plotly-viewer-1)
    -  [`present`](#present) - Returns a subset of a given <code>value</code>.
    -  [`present+paginate-children`](#present+paginate-children)
    -  [`present+paginate-string`](#present+paginate-string)
    -  [`process-blobs`](#process-blobs)
    -  [`process-blocks`](#process-blocks)
    -  [`process-render-fn`](#process-render-fn)
    -  [`process-viewer`](#process-viewer)
    -  [`process-wrapped-value`](#process-wrapped-value)
    -  [`rank-val`](#rank-val)
    -  [`read+inspect-viewer`](#read+inspect-viewer)
    -  [`reagent-viewer`](#reagent-viewer-1)
    -  [`regex-viewer`](#regex-viewer)
    -  [`reset-viewers!`](#reset-viewers-1)
    -  [`resilient-compare`](#resilient-compare)
    -  [`result-block-viewer`](#result-block-viewer)
    -  [`result-viewer`](#result-viewer-1)
    -  [`row`](#row-1)
    -  [`row-viewer`](#row-viewer)
    -  [`rpad-vec`](#rpad-vec)
    -  [`sequential-viewer`](#sequential-viewer)
    -  [`set-viewer`](#set-viewer)
    -  [`string-viewer`](#string-viewer-1)
    -  [`symbol-viewer`](#symbol-viewer)
    -  [`table`](#table-1)
    -  [`table-body-viewer`](#table-body-viewer)
    -  [`table-error-viewer`](#table-error-viewer)
    -  [`table-head-viewer`](#table-head-viewer)
    -  [`table-markup-viewer`](#table-markup-viewer)
    -  [`table-missing-viewer`](#table-missing-viewer)
    -  [`table-row-viewer`](#table-row-viewer)
    -  [`table-viewer`](#table-viewer)
    -  [`tagged-value-viewer`](#tagged-value-viewer)
    -  [`tex`](#tex-1)
    -  [`throwable-viewer`](#throwable-viewer-1)
    -  [`transform-result`](#transform-result)
    -  [`update-table-viewers`](#update-table-viewers)
    -  [`update-val`](#update-val-1)
    -  [`update-viewers`](#update-viewers-1)
    -  [`use-headers`](#use-headers-1)
    -  [`utc-date-format`](#utc-date-format)
    -  [`var->symbol`](#var->symbol)
    -  [`var-from-def-viewer`](#var-from-def-viewer)
    -  [`var-from-def?`](#var-from-def?)
    -  [`var-viewer`](#var-viewer)
    -  [`vector-viewer`](#vector-viewer)
    -  [`vega-lite-viewer`](#vega-lite-viewer-1)
    -  [`viewer-eval?`](#viewer-eval?)
    -  [`viewer-fn?`](#viewer-fn?)
    -  [`viewer-for`](#viewer-for)
    -  [`vl`](#vl-1)
    -  [`when-wrapped`](#when-wrapped)
    -  [`width`](#width) - Returns the <code>:nextjournal/width</code> for a given wrapped value <code>x</code>, <code>nil</code> otherwise.
    -  [`with-block-viewer`](#with-block-viewer)
    -  [`with-md-viewer`](#with-md-viewer)
    -  [`with-viewer`](#with-viewer-1) - Wraps the given value <code>x</code> and associates it with the given <code>viewer</code>.
    -  [`with-viewer-extracting-opts`](#with-viewer-extracting-opts)
    -  [`with-viewers`](#with-viewers-1) - Binds viewers to types, eg {:boolean view-fn}.
    -  [`wrapped-value?`](#wrapped-value?) - Tests if <code>x</code> is a map containing a <code>:nextjournal/value</code>.
-  [`nextjournal.clerk.webserver`](#nextjournalclerkwebserver) 
    -  [`!clients`](#clients)
    -  [`!doc`](#doc-1)
    -  [`!error`](#error-1)
    -  [`!server`](#server)
    -  [`app`](#app)
    -  [`broadcast!`](#broadcast)
    -  [`extract-blob-opts`](#extract-blob-opts)
    -  [`extract-viewer-evals`](#extract-viewer-evals)
    -  [`get-fetch-opts`](#get-fetch-opts)
    -  [`halt!`](#halt-1)
    -  [`help-doc`](#help-doc)
    -  [`serve!`](#serve-1)
    -  [`serve-blob`](#serve-blob)
    -  [`show-error!`](#show-error)
    -  [`update-doc!`](#update-doc)
    -  [`update-if`](#update-if)
# nextjournal.clerk 


Clerk's Public API.



## `->value`
``` clojure

(->value x)
```


Takes `x` and returns the `:nextjournal/value` from it, or otherwise `x` unmodified.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L131-L134)</sub>
## `add-viewers`
``` clojure

(add-viewers added-viewers)
(add-viewers viewers added-viewers)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L101-L103)</sub>
## `add-viewers!`
``` clojure

(add-viewers! viewers)
```


Adds `viewers` to the viewers associated with the current `*ns*`.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L118-L120)</sub>
## `build-static-app!`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L360-L360)</sub>
## `clear-cache!`
``` clojure

(clear-cache!)
```


Clears the in-memory and file-system caches.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L362-L370)</sub>
## `code`
``` clojure

(code code-string-or-form)
(code viewer-opts code-string-or-form)
```


Displays `x` as syntax highlighted Clojure code.

  A string is shown as-is, any other arg will be pretty-printed via `clojure.pprint/pprint`.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L273-L284)</sub>
## `col`
``` clojure

(col & xs)
```


Displays `xs` as columns.

  Treats the first argument as `viewer-opts` if it is a map but not a `wrapped-value?`.

  The `viewer-opts` map can contain the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L240-L250)</sub>
## `defcached`
``` clojure

(defcached name expr)
```


Macro.


Like `clojure.core/def` but with Clerk's caching of the value.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L383-L387)</sub>
## `doc-url`
``` clojure

(doc-url path)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L295-L296)</sub>
## `eval-cljs-str`
``` clojure

(eval-cljs-str code-string)
```


Evaluates the given ClojureScript `code-string` in the browser.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L286-L289)</sub>
## `example`
``` clojure

(example & body)
```


Macro.


Evaluates the expressions in `body` showing code next to results in Clerk.

  Does nothing outside of Clerk, like `clojure.core/comment`.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L298-L305)</sub>
## `file->viewer`
``` clojure

(file->viewer file)
(file->viewer opts file)
```


Evaluates the given `file` and returns it's viewer representation.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L307-L310)</sub>
## `get-default-viewers`
``` clojure

(get-default-viewers)
```


Gets Clerk's default viewers.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L96-L99)</sub>
## `halt!`
``` clojure

(halt!)
```


Stops the Clerk webserver and file watcher.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L347-L351)</sub>
## `halt-watcher!`
``` clojure

(halt-watcher!)
```


Halts the filesystem watcher when active.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L314-L320)</sub>
## `hide-result`
``` clojure

(hide-result x)
(hide-result viewer-opts x)
```


Deprecated, please put ^{:nextjournal.clerk/visibility {:result :hide}} metadata on the form instead.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L263-L270)</sub>
## `html`
``` clojure

(html x)
(html viewer-opts x)
```


Displays `x` using the html-viewer. Supports HTML and SVG as strings or hiccup.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L159-L168)</sub>
## `mark-presented`
``` clojure

(mark-presented wrapped-value)
```


Marks the given `wrapped-value` so that it will be passed unmodified to the browser.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L143-L146)</sub>
## `mark-preserve-keys`
``` clojure

(mark-preserve-keys wrapped-value)
```


Marks the given `wrapped-value` so that the keys will be passed unmodified to the browser.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L149-L152)</sub>
## `md`
``` clojure

(md x)
(md viewer-opts x)
```


Displays `x` with the markdown viewer.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L170-L179)</sub>
## `notebook`

Experimental notebook viewer. You probably should not use this.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L291-L293)</sub>
## `plotly`
``` clojure

(plotly x)
(plotly viewer-opts x)
```


Displays `x` with the plotly viewer.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L181-L190)</sub>
## `recompute!`
``` clojure

(recompute!)
```


Recomputes the currently visible doc, without parsing it.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L40-L46)</sub>
## `reset-viewers!`
``` clojure

(reset-viewers! viewers)
```


Resets the viewers associated with the current `*ns*` to `viewers`.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L113-L115)</sub>
## `row`
``` clojure

(row & xs)
```


Displays `xs` as rows.

  Treats the first argument as `viewer-opts` if it is a map but not a `wrapped-value?`.

  The `viewer-opts` map can contain the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L228-L238)</sub>
## `serve!`
``` clojure

(serve! {:as config, :keys [browse? watch-paths port show-filter-fn], :or {port 7777}})
```


Main entrypoint to Clerk taking an configurations map.

  Will obey the following optional configuration entries:

  * a `:port` for the webserver to listen on, defaulting to `7777`
  * `:browse?` will open Clerk in a browser after it's been started
  * a sequence of `:watch-paths` that Clerk will watch for file system events and show any changed file
  * a `:show-filter-fn` to restrict when to re-evaluate or show a notebook as a result of file system event. Useful for e.g. pinning a notebook. Will be called with the string path of the changed file.

  Can be called multiple times and Clerk will happily serve you according to the latest config.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L322-L345)</sub>
## `set-viewers!`
``` clojure

(set-viewers! viewers)
```


Deprecated, please use `add-viewers!` instead.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L123-L128)</sub>
## `show!`
``` clojure

(show! file)
```


Evaluates the Clojure source in `file` and makes Clerk show it in the browser.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L20-L36)</sub>
## `table`
``` clojure

(table xs)
(table viewer-opts xs)
```


Displays `xs` using the table viewer.

  Performs normalization on the data, supporting:
  * seqs of maps
  * maps of seqs
  * seqs of seqs

  If you want a header for seqs of seqs use `use-headers`.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L210-L226)</sub>
## `tex`
``` clojure

(tex x)
(tex viewer-opts x)
```


Displays `x` as LaTeX using KaTeX.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L252-L261)</sub>
## `update-val`
``` clojure

(update-val f & args)
```


Take a function `f` and optional `args` and returns a function to update only the `:nextjournal/value` inside a wrapped-value.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L137-L140)</sub>
## `update-viewers`
``` clojure

(update-viewers viewers select-fn->update-fn)
```


Takes `viewers` and a `select-fn->update-fn` map returning updated
  viewers with each viewer matching `select-fn` will by updated using
  the function in `update-fn`.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L105-L110)</sub>
## `use-headers`
``` clojure

(use-headers xs)
```


Treats the first element of the seq `xs` as a header for the table.

  Meant to be used in combination with `table`.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L203-L208)</sub>
## `valuehash`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L358-L358)</sub>
## `vl`
``` clojure

(vl x)
(vl viewer-opts x)
```


Displays `x` with the vega embed viewer, supporting both vega-lite and vega.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L192-L201)</sub>
## `with-cache`
``` clojure

(with-cache form)
```


Macro.


An expression evaluated with Clerk's caching.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L375-L379)</sub>
## `with-viewer`
``` clojure

(with-viewer viewer x)
(with-viewer viewer viewer-opts x)
```


Displays `x` using the given `viewer`.

  Takes an optional second `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L80-L89)</sub>
## `with-viewers`
``` clojure

(with-viewers viewers x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L92-L94)</sub>
# nextjournal.clerk.builder 


Clerk's Static App Builder.



## `->html-extension`
``` clojure

(->html-extension path)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/builder.clj#L58-L59)</sub>
## `build-static-app!`
``` clojure

(build-static-app! opts)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/builder.clj#L132-L161)</sub>
## `clerk-docs`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/builder.clj#L12-L47)</sub>
## `expand-paths`
``` clojure

(expand-paths paths)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/builder.clj#L116-L124)</sub>
## `process-build-opts`
``` clojure

(process-build-opts {:as opts, :keys [paths]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/builder.clj#L69-L73)</sub>
## `stdout-reporter`
``` clojure

(stdout-reporter {:as event, :keys [stage state duration doc]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/builder.clj#L103-L114)</sub>
## `strip-index`
``` clojure

(strip-index path)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/builder.clj#L50-L51)</sub>
## `write-static-app!`
``` clojure

(write-static-app! opts docs)
```


Creates a static html app of the seq of `docs`. Customizable with an `opts` map with keys:

  - `:paths` a vector of relative paths to notebooks to include in the build
  - `:bundle?` builds a single page app versus a folder with an html page for each notebook (defaults to `true`)
  - `:out-path` a relative path to a folder to contain the static pages (defaults to `"public/build"`)
  - `:git/sha`, `:git/url` when both present, each page displays a link to `(str url "blob" sha path-to-notebook)`
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/builder.clj#L75-L101)</sub>
# nextjournal.clerk.classpath 





## `classpath-directories`
``` clojure

(classpath-directories)
```


Like `clojure.java.classpath/classpath-directories` but using the `system-classpath` which cider doesn't break,
  see https://github.com/clojure-emacs/cider-nrepl/pull/668
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/classpath.clj#L7-L11)</sub>
## `classpath-jarfiles`
``` clojure

(classpath-jarfiles)
```


Like `clojure.java.classpath/classpath-jarfiles` but using the `system-classpath` which cider doesn't break,
  see https://github.com/clojure-emacs/cider-nrepl/pull/668
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/classpath.clj#L14-L18)</sub>
# nextjournal.clerk.config 





## `!asset-map`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/config.clj#L24-L27)</sub>
## `!resource->url`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/config.clj#L29-L33)</sub>
## `*bounded-count-limit*`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/config.clj#L44-L50)</sub>
## `*in-clerk*`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/config.clj#L42-L42)</sub>
## `cache-dir`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/config.clj#L11-L13)</sub>
## `cache-disabled?`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/config.clj#L15-L17)</sub>
## `gs-url-prefix`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/config.clj#L7-L7)</sub>
## `lookup-hash`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/config.clj#L8-L8)</sub>
## `lookup-url`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/config.clj#L9-L9)</sub>
## `resource-manifest-from-props`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/config.clj#L19-L22)</sub>
# nextjournal.clerk.eval 


Clerk's incremental evaluation with in-memory and disk-persisted caching layers.



## `+eval-results`
``` clojure

(+eval-results in-memory-cache parsed-doc)
```


Evaluates the given `parsed-doc` using the `in-memory-cache` and augments it with the results.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L195-L203)</sub>
## `->cache-file`
``` clojure

(->cache-file hash)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L30-L31)</sub>
## `elapsed-ms`
``` clojure

(elapsed-ms from)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L55-L56)</sub>
## `eval-analyzed-doc`
``` clojure

(eval-analyzed-doc {:as analyzed-doc, :keys [->hash blocks]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L177-L193)</sub>
## `eval-doc`
``` clojure

(eval-doc doc)
(eval-doc in-memory-cache doc)
```


Evaluates the given `doc`.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L205-L208)</sub>
## `eval-file`
``` clojure

(eval-file file)
(eval-file in-memory-cache file)
```


Reads given `file` (using `slurp`) and evaluates it.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L210-L216)</sub>
## `eval-string`
``` clojure

(eval-string code-string)
(eval-string in-memory-cache code-string)
```


Evaluated the given `code-string` using the optional `in-memory-cache` map.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L222-L226)</sub>
## `hash+store-in-cas!`
``` clojure

(hash+store-in-cas! x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L39-L46)</sub>
## `maybe-eval-viewers`
``` clojure

(maybe-eval-viewers {:as opts, :nextjournal/keys [viewer viewers]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L134-L139)</sub>
## `read+eval-cached`
``` clojure

(read+eval-cached {:as _doc, :keys [blob->result ->analysis-info ->hash]} codeblock)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L141-L168)</sub>
## `thaw-from-cas`
``` clojure

(thaw-from-cas hash)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L48-L50)</sub>
## `time-ms`
``` clojure

(time-ms expr)
```


Macro.


Pure version of `clojure.core/time`. Returns a map with `:result` and `:time-ms` keys.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L58-L64)</sub>
## `wrapped-with-metadata`
``` clojure

(wrapped-with-metadata value hash)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/eval.clj#L33-L35)</sub>
# nextjournal.clerk.graph-visualizer 





## `show-graph`
``` clojure

(show-graph {:keys [graph var->hash]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/graph_visualizer.clj#L9-L17)</sub>
## `viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/graph_visualizer.clj#L5-L6)</sub>
# nextjournal.clerk.parser 


Clerk's Parser turns Clojure & Markdown files and strings into Clerk documents.



## `->doc-settings`
``` clojure

(->doc-settings first-form)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L98-L105)</sub>
## `->doc-visibility`
``` clojure

(->doc-visibility form)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L79-L89)</sub>
## `->visibility`
``` clojure

(->visibility form)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L65-L69)</sub>
## `add-block-visibility`
``` clojure

(add-block-visibility {:as analyzed-doc, :keys [blocks]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L109-L118)</sub>
## `auto-resolves`
``` clojure

(auto-resolves ns)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L120-L124)</sub>
## `code-cell?`
``` clojure

(code-cell? {:as node, :keys [type]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L196-L197)</sub>
## `code-tags`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L139-L140)</sub>
## `ns?`
``` clojure

(ns? form)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L14-L15)</sub>
## `parse-clojure-string`
``` clojure

(parse-clojure-string s)
(parse-clojure-string opts s)
(parse-clojure-string {:as _opts, :keys [doc?]} initial-state s)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L145-L189)</sub>
## `parse-file`
``` clojure

(parse-file file)
(parse-file opts file)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L221-L226)</sub>
## `parse-markdown-cell`
``` clojure

(parse-markdown-cell {:as state, :keys [nodes]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L199-L202)</sub>
## `parse-markdown-string`
``` clojure

(parse-markdown-string {:keys [doc?]} s)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L204-L219)</sub>
## `parse-visibility`
``` clojure

(parse-visibility form visibility)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L52-L60)</sub>
## `read-string`
``` clojure

(read-string s)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L129-L135)</sub>
## `remove-leading-semicolons`
``` clojure

(remove-leading-semicolons s)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L17-L18)</sub>
## `visibility-marker?`
``` clojure

(visibility-marker? form)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L49-L50)</sub>
## `whitespace-on-line-tags`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/parser.clj#L142-L143)</sub>
# nextjournal.clerk.sci-viewer 





## `!doc`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L520-L520)</sub>
## `!edamame-opts`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L179-L198)</sub>
## `!error`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L521-L521)</sub>
## `!eval-counter`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L122-L122)</sub>
## `!sci-ctx`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L726-L737)</sub>
## `!viewers`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L522-L522)</sub>
## `check-icon`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L454-L456)</sub>
## `clerk-eval`
``` clojure

(clerk-eval form)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L600-L601)</sub>
## `code-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L622-L622)</sub>
## `coll-view`
``` clojure

(coll-view xs {:as opts, :keys [path viewer !expanded-at], :or {path []}})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L363-L377)</sub>
## `coll-viewer`
``` clojure

(coll-viewer xs opts)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L379-L379)</sub>
## `color-classes`
``` clojure

(color-classes selected?)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L31-L36)</sub>
## `dark-mode-toggle`
``` clojure

(dark-mode-toggle !state)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L58-L102)</sub>
## `default-viewers`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L529-L529)</sub>
## `doc-url`

Stub implementation to be replaced during static site generation. Clerk is only serving one page currently.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L672-L674)</sub>
## `elision-viewer`
``` clojure

(elision-viewer {:as fetch-opts, :keys [total offset unbounded?]} _)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L381-L390)</sub>
## `error-badge`
``` clojure

(error-badge & content)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L217-L221)</sub>
## `error-boundary`
``` clojure

(error-boundary !error & _)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L238-L246)</sub>
## `error-view`
``` clojure

(error-view error)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L223-L236)</sub>
## `eval-form`
``` clojure

(eval-form f)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L739-L740)</sub>
## `eval-viewer-fn`
``` clojure

(eval-viewer-fn eval-f form)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L174-L177)</sub>
## `expand-button`
``` clojure

(expand-button !expanded-at opening-paren path)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L349-L361)</sub>
## `expand-icon`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L626-L628)</sub>
## `expand-style`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L329-L339)</sub>
## `expandable?`
``` clojure

(expandable? xs)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L320-L321)</sub>
## `fetch!`
``` clojure

(fetch! {:keys [blob-id]} opts)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L248-L256)</sub>
## `foldable-code-viewer`
``` clojure

(foldable-code-viewer code-string)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L630-L663)</sub>
## `html`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L615-L616)</sub>
## `html-render`
``` clojure

(html-render markup)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L606-L610)</sub>
## `html-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L612-L613)</sub>
## `in-process-fetch`
``` clojure

(in-process-fetch value opts)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L564-L565)</sub>
## `inspect`
``` clojure

(inspect value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L567-L575)</sub>
## `inspect-children`
``` clojure

(inspect-children opts)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L324-L327)</sub>
## `inspect-presented`
``` clojure

(inspect-presented x)
(inspect-presented opts x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L552-L562)</sub>
## `katex-viewer`
``` clojure

(katex-viewer tex-string {:keys [inline?]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L603-L604)</sub>
## `local-storage-dark-mode-key`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L104-L104)</sub>
## `map-view`
``` clojure

(map-view xs {:as opts, :keys [path viewer !expanded-at], :or {path []}})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L392-L405)</sub>
## `map-viewer`
``` clojure

(map-viewer xs opts)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L407-L407)</sub>
## `mathjax-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L621-L621)</sub>
## `mount`
``` clojure

(mount)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L595-L598)</sub>
## `nbsp`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L40-L40)</sub>
## `normalize-viewer-meta`
``` clojure

(normalize-viewer-meta x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L514-L518)</sub>
## `notebook`
``` clojure

(notebook {:as _doc, xs :blocks, :keys [toc toc-visibility]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L124-L171)</sub>
## `number-viewer`
``` clojure

(number-viewer num)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L432-L434)</sub>
## `opts->query`
``` clojure

(opts->query opts)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L204-L208)</sub>
## `plotly-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L623-L623)</sub>
## `quoted-string-viewer`
``` clojure

(quoted-string-viewer s {:as opts, :keys [path viewer !expanded-at], :or {path []}})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L420-L430)</sub>
## `read-result`
``` clojure

(read-result {:nextjournal/keys [edn string]} !error)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L258-L264)</sub>
## `read-string`
``` clojure

(read-string s)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L201-L202)</sub>
## `reagent-viewer`
``` clojure

(reagent-viewer x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L618-L619)</sub>
## `render-with-viewer`
``` clojure

(render-with-viewer opts viewer value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L531-L548)</sub>
## `result-viewer`
``` clojure

(result-viewer {:as result, :nextjournal/keys [fetch-opts hash]} _opts)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L266-L301)</sub>
## `root`
``` clojure

(root)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L577-L582)</sub>
## `sci-viewer-namespace`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L676-L724)</sub>
## `set-dark-mode!`
``` clojure

(set-dark-mode! dark-mode?)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L106-L111)</sub>
## `set-state`
``` clojure

(set-state {:as state, :keys [doc error remount?]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L586-L593)</sub>
## `set-viewers!`
``` clojure

(set-viewers! scope viewers)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L524-L527)</sub>
## `setup-dark-mode!`
``` clojure

(setup-dark-mode! !state)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L113-L120)</sub>
## `sort!`
``` clojure

(sort! !sort i k)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L436-L440)</sub>
## `sort-data`
``` clojure

(sort-data {:keys [sort-index sort-order]} {:as data, :keys [head rows]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L442-L448)</sub>
## `string-viewer`
``` clojure

(string-viewer s {:as opts, :keys [path !expanded-at], :or {path []}})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L409-L418)</sub>
## `table-error`
``` clojure

(table-error [data])
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L458-L482)</sub>
## `tagged-value`
``` clojure

(tagged-value tag value)
(tagged-value {:keys [space?]} tag value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L508-L512)</sub>
## `throwable-viewer`
``` clojure

(throwable-viewer {:keys [via trace]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L485-L506)</sub>
## `toc-items`
``` clojure

(toc-items items)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L44-L56)</sub>
## `toggle-expanded`
``` clojure

(toggle-expanded !expanded-at path event)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L303-L318)</sub>
## `triangle`
``` clojure

(triangle expanded?)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L341-L345)</sub>
## `triangle-spacer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L347-L347)</sub>
## `unreadable-edn-viewer`
``` clojure

(unreadable-edn-viewer edn)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L214-L215)</sub>
## `url-for`
``` clojure

(url-for {:as src, :keys [blob-id]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L666-L670)</sub>
## `valid-react-element?`
``` clojure

(valid-react-element? x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L550-L550)</sub>
## `vega-lite-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L624-L624)</sub>
## `x-icon`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/sci_viewer.cljs#L450-L452)</sub>
# nextjournal.clerk.static-app 





## `!match`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#L114-L114)</sub>
## `!state`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#L115-L115)</sub>
## `doc-url`
``` clojure

(doc-url {:keys [path->url current-path bundle?]} path)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#L15-L27)</sub>
## `get-routes`
``` clojure

(get-routes docs)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#L108-L111)</sub>
## `hiccup`
``` clojure

(hiccup hiccup)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#L29-L31)</sub>
## `index`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#Lnull-Lnull)</sub>
## `init`
``` clojure

(init {:as state, :keys [bundle? path->doc path->url current-path]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#L135-L146)</sub>
## `mount`
``` clojure

(mount)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#L127-L129)</sub>
## `root`
``` clojure

(root)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#L117-L125)</sub>
## `show`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#Lnull-Lnull)</sub>
## `ssr`
``` clojure

(ssr state-str)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/static_app.cljs#L148-L150)</sub>
# nextjournal.clerk.view 





## `->html`
``` clojure

(->html {:keys [conn-ws?], :or {conn-ws? true}} state)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/view.clj#L35-L51)</sub>
## `->static-app`
``` clojure

(->static-app {:as state, :keys [current-path]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/view.clj#L53-L66)</sub>
## `doc->html`
``` clojure

(doc->html doc error)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/view.clj#L68-L69)</sub>
## `doc->static-html`
``` clojure

(doc->static-html doc)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/view.clj#L71-L72)</sub>
## `doc->viewer`
``` clojure

(doc->viewer doc)
(doc->viewer opts {:as doc, :keys [ns file]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/view.clj#L8-L12)</sub>
## `include-css+js`
``` clojure

(include-css+js)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/view.clj#L28-L33)</sub>
## `include-viewer-css`
``` clojure

(include-viewer-css)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/view.clj#L19-L26)</sub>
# nextjournal.clerk.viewer 





## `!viewers`

atom containing a map of and per-namespace viewers or `:defaults` overridden viewers.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L819-L822)</sub>
## `->ViewerEval`
``` clojure

(->ViewerEval form)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L24-L24)</sub>
## `->ViewerFn`
``` clojure

(->ViewerFn form f)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L26-L29)</sub>
## `->display`
``` clojure

(->display {:as code-cell, :keys [result visibility]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L369-L373)</sub>
## `->edn`
``` clojure

(->edn x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L275-L277)</sub>
## `->fetch-opts`
``` clojure

(->fetch-opts wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L997-L999)</sub>
## `->opts`
``` clojure

(->opts wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L889-L890)</sub>
## `->value`
``` clojure

(->value x)
```


Takes `x` and returns the `:nextjournal/value` from it, or otherwise `x` unmodified.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L78-L83)</sub>
## `->viewer`
``` clojure

(->viewer x)
```


Returns the `:nextjournal/viewer` for a given wrapped value `x`, `nil` otherwise.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L88-L92)</sub>
## `->viewer-eval`
``` clojure

(->viewer-eval form)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L41-L42)</sub>
## `->viewer-fn`
``` clojure

(->viewer-fn form)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L38-L39)</sub>
## `->viewers`
``` clojure

(->viewers x)
```


Returns the `:nextjournal/viewers` for a given wrapped value `x`, `nil` otherwise.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L98-L102)</sub>
## `ViewerEval`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L24-L24)</sub>
## `ViewerFn`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L26-L29)</sub>
## `add-viewers`
``` clojure

(add-viewers added-viewers)
(add-viewers viewers added-viewers)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L404-L406)</sub>
## `add-viewers!`
``` clojure

(add-viewers! viewers)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1267-L1269)</sub>
## `apply-viewer-unwrapping-var-from-def`
``` clojure

(apply-viewer-unwrapping-var-from-def {:as result, :nextjournal/keys [value viewer]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L281-L291)</sub>
## `apply-viewers`
``` clojure

(apply-viewers x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L909-L910)</sub>
## `apply-viewers*`
``` clojure

(apply-viewers* wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L892-L907)</sub>
## `assign-closing-parens`
``` clojure

(assign-closing-parens node)
(assign-closing-parens closing-parens node)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1235-L1258)</sub>
## `assign-content-lengths`
``` clojure

(assign-content-lengths wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1091-L1115)</sub>
## `assign-expanded-at`
``` clojure

(assign-expanded-at wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1152-L1153)</sub>
## `base64-encode-value`
``` clojure

(base64-encode-value {:as result, :nextjournal/keys [content-type]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L297-L299)</sub>
## `boolean-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L563-L564)</sub>
## `bounded-count-opts`
``` clojure

(bounded-count-opts n xs)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L935-L944)</sub>
## `buffered-image-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L598-L610)</sub>
## `char-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L542-L543)</sub>
## `code`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1284-L1284)</sub>
## `code-block-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L708-L712)</sub>
## `code-folded-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L663-L664)</sub>
## `code-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L660-L661)</sub>
## `col`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1281-L1281)</sub>
## `col-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L678-L684)</sub>
## `compute-expanded-at`
``` clojure

(compute-expanded-at {:as state, :keys [expanded-at]} {:nextjournal/keys [value], :keys [path]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1145-L1150)</sub>
## `count-viewers`
``` clojure

(count-viewers x)
```


Helper function to walk a given `x` and replace the viewers with their counts. Useful for debugging.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L924-L933)</sub>
## `datafied?`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L248-L249)</sub>
## `datafy-scope`
``` clojure

(datafy-scope scope)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L465-L469)</sub>
## `default-viewers`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L773-L817)</sub>
## `demunge-ex-data`
``` clojure

(demunge-ex-data ex-data)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L220-L221)</sub>
## `desc->values`
``` clojure

(desc->values desc)
```


Takes a `description` and returns its value. Inverse of `present`. Mostly useful for debugging.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1201-L1213)</sub>
## `doc-url`
``` clojure

(doc-url path)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1285-L1286)</sub>
## `drop+take-xf`
``` clojure

(drop+take-xf {:keys [n offset], :or {offset 0}})
```


Takes a map with optional `:n` and `:offset` and returns a transducer that drops `:offset` and takes `:n`.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L949-L955)</sub>
## `elision-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L631-L632)</sub>
## `ensure-sorted`
``` clojure

(ensure-sorted xs)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L841-L846)</sub>
## `ensure-wrapped`
``` clojure

(ensure-wrapped x)
(ensure-wrapped x v)
```


Ensures `x` is wrapped in a map under a `:nextjournal/value` key.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L70-L73)</sub>
## `ensure-wrapped-with-viewers`
``` clojure

(ensure-wrapped-with-viewers x)
(ensure-wrapped-with-viewers viewers x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L879-L884)</sub>
## `eval-cljs-result-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1297-L1300)</sub>
## `eval-cljs-str`
``` clojure

(eval-cljs-str code-string)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1302-L1308)</sub>
## `example-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1312-L1324)</sub>
## `examples-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1326-L1332)</sub>
## `fallback-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L628-L629)</sub>
## `fetch-all`
``` clojure

(fetch-all _opts _xs)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L242-L243)</sub>
## `find-elision`
``` clojure

(find-elision desc)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L993-L995)</sub>
## `find-named-viewer`
``` clojure

(find-named-viewer viewers viewer-name)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L855-L856)</sub>
## `find-viewer`
``` clojure

(find-viewer viewers select-fn)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L849-L850)</sub>
## `get-default-viewers`
``` clojure

(get-default-viewers)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L329-L330)</sub>
## `get-elision`
``` clojure

(get-elision wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1001-L1003)</sub>
## `get-fetch-opts-n`
``` clojure

(get-fetch-opts-n wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1009-L1010)</sub>
## `get-safe`
``` clojure

(get-safe key)
(get-safe map key)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L167-L172)</sub>
## `get-viewers`
``` clojure

(get-viewers scope)
(get-viewers scope value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L332-L337)</sub>
## `hide-result`
``` clojure

(hide-result x)
(hide-result _viewer-opts x)
```


Deprecated, please put ^{:nextjournal.clerk/visibility {:result :hide}} metadata on the form instead.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1288-L1295)</sub>
## `hide-result-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L366-L367)</sub>
## `html`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1275-L1275)</sub>
## `html-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L640-L644)</sub>
## `ideref-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L612-L621)</sub>
## `inherit-opts`
``` clojure

(inherit-opts {:as wrapped-value, :nextjournal/keys [viewers]} value path-segment)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1012-L1017)</sub>
## `inspect-fn`
``` clojure

(inspect-fn)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L227-L227)</sub>
## `inspect-wrapped-value`
``` clojure

(inspect-wrapped-value wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L231-L232)</sub>
## `into-markup`
``` clojure

(into-markup markup)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L257-L272)</sub>
## `js-array-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L740-L746)</sub>
## `js-object-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L722-L737)</sub>
## `katex-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L634-L635)</sub>
## `keyword-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L557-L558)</sub>
## `make-elision`
``` clojure

(make-elision viewers fetch-opts)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L985-L989)</sub>
## `map->ViewerEval`
``` clojure

(map->ViewerEval m)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L24-L24)</sub>
## `map->ViewerFn`
``` clojure

(map->ViewerFn m)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L26-L29)</sub>
## `map-entry-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L566-L567)</sub>
## `map-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L586-L587)</sub>
## `mark-presented`
``` clojure

(mark-presented wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L236-L237)</sub>
## `mark-preserve-keys`
``` clojure

(mark-preserve-keys wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L239-L240)</sub>
## `markdown-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L652-L658)</sub>
## `markdown-viewers`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L479-L540)</sub>
## `mathjax-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L637-L638)</sub>
## `maybe-store-result-as-file`
``` clojure

(maybe-store-result-as-file
 {:as _doc+blob-opts, :keys [blob-id file out-path]}
 {:as result, :nextjournal/keys [content-type value]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L302-L314)</sub>
## `md`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1276-L1276)</sub>
## `merge-presentations`
``` clojure

(merge-presentations root more elision)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1222-L1232)</sub>
## `missing-pred`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L180-L181)</sub>
## `nil-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L560-L561)</sub>
## `normalize-map-of-seq`
``` clojure

(normalize-map-of-seq m)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L193-L200)</sub>
## `normalize-seq-of-map`
``` clojure

(normalize-seq-of-map s)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L187-L190)</sub>
## `normalize-seq-of-seq`
``` clojure

(normalize-seq-of-seq s)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L183-L185)</sub>
## `normalize-seq-to-vec`
``` clojure

(normalize-seq-to-vec {:keys [head rows]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L202-L204)</sub>
## `normalize-table-data`
``` clojure

(normalize-table-data data)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L212-L218)</sub>
## `normalize-viewer`
``` clojure

(normalize-viewer viewer)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L119-L124)</sub>
## `normalize-viewer-opts`
``` clojure

(normalize-viewer-opts opts)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L111-L117)</sub>
## `notebook`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1283-L1283)</sub>
## `notebook-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L765-L771)</sub>
## `number-hex-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L551-L552)</sub>
## `number-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L548-L549)</sub>
## `path-to-value`
``` clojure

(path-to-value path)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1219-L1220)</sub>
## `plotly`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1277-L1277)</sub>
## `plotly-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L646-L647)</sub>
## `present`
``` clojure

(present x)
(present x opts)
```


Returns a subset of a given `value`.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1169-L1180)</sub>
## `present+paginate-children`
``` clojure

(present+paginate-children {:as wrapped-value, :nextjournal/keys [viewers preserve-keys?], :keys [!budget budget]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1019-L1038)</sub>
## `present+paginate-string`
``` clojure

(present+paginate-string {:as wrapped-value, :nextjournal/keys [viewers viewer value]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1040-L1050)</sub>
## `process-blobs`
``` clojure

(process-blobs {:as doc+blob-opts, :keys [blob-mode blob-id]} presented-result)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L320-L327)</sub>
## `process-blocks`
``` clojure

(process-blocks viewers {:as doc, :keys [ns]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L756-L763)</sub>
## `process-render-fn`
``` clojure

(process-render-fn {:as viewer, :keys [render-fn]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L964-L967)</sub>
## `process-viewer`
``` clojure

(process-viewer viewer)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L969-L974)</sub>
## `process-wrapped-value`
``` clojure

(process-wrapped-value wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L978-L981)</sub>
## `rank-val`
``` clojure

(rank-val val)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L829-L833)</sub>
## `read+inspect-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L572-L575)</sub>
## `reagent-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L666-L667)</sub>
## `regex-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L623-L626)</sub>
## `reset-viewers!`
``` clojure

(reset-viewers! viewers)
(reset-viewers! scope viewers)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1260-L1265)</sub>
## `resilient-compare`
``` clojure

(resilient-compare a b)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L835-L839)</sub>
## `result-block-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L748-L751)</sub>
## `result-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L753-L754)</sub>
## `row`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1280-L1280)</sub>
## `row-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L669-L676)</sub>
## `rpad-vec`
``` clojure

(rpad-vec v length padding)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L177-L178)</sub>
## `sequential-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L583-L584)</sub>
## `set-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L580-L581)</sub>
## `string-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L545-L546)</sub>
## `symbol-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L554-L555)</sub>
## `table`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1279-L1279)</sub>
## `table-body-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L427-L429)</sub>
## `table-error-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L705-L706)</sub>
## `table-head-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L415-L425)</sub>
## `table-markup-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L410-L413)</sub>
## `table-missing-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L408-L408)</sub>
## `table-row-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L431-L436)</sub>
## `table-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L686-L703)</sub>
## `tagged-value-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L714-L719)</sub>
## `tex`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1282-L1282)</sub>
## `throwable-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L594-L596)</sub>
## `transform-result`
``` clojure

(transform-result {cell :nextjournal/value, doc :nextjournal/opts})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L342-L364)</sub>
## `update-table-viewers`
``` clojure

(update-table-viewers viewers)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L438-L458)</sub>
## `update-val`
``` clojure

(update-val f & args)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L474-L475)</sub>
## `update-viewers`
``` clojure

(update-viewers viewers select-fn->update-fn)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L394-L400)</sub>
## `use-headers`
``` clojure

(use-headers s)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L206-L210)</sub>
## `utc-date-format`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L460-L462)</sub>
## `var->symbol`
``` clojure

(var->symbol v)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L589-L589)</sub>
## `var-from-def-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L569-L570)</sub>
## `var-from-def?`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L245-L246)</sub>
## `var-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L591-L592)</sub>
## `vector-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L577-L578)</sub>
## `vega-lite-viewer`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L649-L650)</sub>
## `viewer-eval?`
``` clojure

(viewer-eval? x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L35-L36)</sub>
## `viewer-fn?`
``` clojure

(viewer-fn? x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L32-L33)</sub>
## `viewer-for`
``` clojure

(viewer-for viewers x)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L860-L871)</sub>
## `vl`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L1278-L1278)</sub>
## `when-wrapped`
``` clojure

(when-wrapped f)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L229-L229)</sub>
## `width`
``` clojure

(width x)
```


Returns the `:nextjournal/width` for a given wrapped value `x`, `nil` otherwise.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L104-L108)</sub>
## `with-block-viewer`
``` clojure

(with-block-viewer doc {:as cell, :keys [type]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L379-L392)</sub>
## `with-md-viewer`
``` clojure

(with-md-viewer wrapped-value)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L251-L255)</sub>
## `with-viewer`
``` clojure

(with-viewer viewer x)
(with-viewer viewer viewer-opts x)
```


Wraps the given value `x` and associates it with the given `viewer`. Takes an optional second `viewer-opts` arg.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L133-L140)</sub>
## `with-viewer-extracting-opts`
``` clojure

(with-viewer-extracting-opts viewer & opts+items)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L143-L153)</sub>
## `with-viewers`
``` clojure

(with-viewers viewers x)
```


Binds viewers to types, eg {:boolean view-fn}
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L158-L163)</sub>
## `wrapped-value?`
``` clojure

(wrapped-value? x)
```


Tests if `x` is a map containing a `:nextjournal/value`.
<br><sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/viewer.cljc#L63-L68)</sub>
# nextjournal.clerk.webserver 





## `!clients`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L14-L14)</sub>
## `!doc`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L15-L15)</sub>
## `!error`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L16-L16)</sub>
## `!server`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L112-L112)</sub>
## `app`
``` clojure

(app {:as req, :keys [uri]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L63-L81)</sub>
## `broadcast!`
``` clojure

(broadcast! msg)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L21-L23)</sub>
## `extract-blob-opts`
``` clojure

(extract-blob-opts {:as _req, :keys [uri query-string]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L59-L61)</sub>
## `extract-viewer-evals`
``` clojure

(extract-viewer-evals {:as _doc, :keys [blocks]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L85-L89)</sub>
## `get-fetch-opts`
``` clojure

(get-fetch-opts query-string)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L34-L39)</sub>
## `halt!`
``` clojure

(halt!)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L114-L118)</sub>
## `help-doc`
<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L11-L12)</sub>
## `serve!`
``` clojure

(serve! {:keys [port], :or {port 7777}})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L120-L126)</sub>
## `serve-blob`
``` clojure

(serve-blob {:as doc, :keys [blob->result ns]} {:keys [blob-id fetch-opts]})
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L44-L57)</sub>
## `show-error!`
``` clojure

(show-error! e)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L102-L103)</sub>
## `update-doc!`
``` clojure

(update-doc! doc)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L93-L97)</sub>
## `update-if`
``` clojure

(update-if m k f)
```

<sub>[source](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk/webserver.clj#L27-L30)</sub>
