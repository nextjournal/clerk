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
