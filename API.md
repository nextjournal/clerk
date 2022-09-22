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
    -  [`read-string`](#read-string)
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
