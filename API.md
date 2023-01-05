# Table of contents
-  [`nextjournal.clerk`](#nextjournal.clerk)  - Clerk's Public API.
    -  [`->value`](#nextjournal.clerk/->value) - Takes <code>x</code> and returns the <code>:nextjournal/value</code> from it, or otherwise <code>x</code> unmodified.
    -  [`add-viewers`](#nextjournal.clerk/add-viewers)
    -  [`add-viewers!`](#nextjournal.clerk/add-viewers!) - Adds <code>viewers</code> to the viewers associated with the current <code>*ns*</code>.
    -  [`build!`](#nextjournal.clerk/build!) - Creates a static html build from a collection of notebooks.
    -  [`build-static-app!`](#nextjournal.clerk/build-static-app!)
    -  [`caption`](#nextjournal.clerk/caption) - Displays <code>content</code> with <code>text</code> as caption below it.
    -  [`clear-cache!`](#nextjournal.clerk/clear-cache!) - Clears the in-memory and file-system caches when called with no arguments.
    -  [`code`](#nextjournal.clerk/code) - Displays <code>x</code> as syntax highlighted Clojure code.
    -  [`col`](#nextjournal.clerk/col) - Displays <code>xs</code> as columns.
    -  [`defcached`](#nextjournal.clerk/defcached) - Like <code>clojure.core/def</code> but with Clerk's caching of the value.
    -  [`doc-url`](#nextjournal.clerk/doc-url)
    -  [`eval-cljs`](#nextjournal.clerk/eval-cljs) - Evaluates the given ClojureScript forms in the browser.
    -  [`eval-cljs-str`](#nextjournal.clerk/eval-cljs-str) - Evaluates the given ClojureScript <code>code-string</code> in the browser.
    -  [`example`](#nextjournal.clerk/example) - Evaluates the expressions in <code>body</code> showing code next to results in Clerk.
    -  [`file->viewer`](#nextjournal.clerk/file->viewer) - Evaluates the given <code>file</code> and returns it's viewer representation.
    -  [`get-default-viewers`](#nextjournal.clerk/get-default-viewers) - Gets Clerk's default viewers.
    -  [`halt!`](#nextjournal.clerk/halt!) - Stops the Clerk webserver and file watcher.
    -  [`halt-watcher!`](#nextjournal.clerk/halt-watcher!) - Halts the filesystem watcher when active.
    -  [`hide-result`](#nextjournal.clerk/hide-result) - Deprecated, please put <code>^{:nextjournal.clerk/visibility {:result :hide}}</code> metadata on the form instead.
    -  [`html`](#nextjournal.clerk/html) - Displays <code>x</code> using the html-viewer.
    -  [`image`](#nextjournal.clerk/image) - Creates a <code>java.awt.image.BufferedImage</code> from <code>url</code>, which can be a <code>java.net.URL</code> or a string, and displays it using the <code>buffered-image-viewer</code>.
    -  [`mark-presented`](#nextjournal.clerk/mark-presented) - Marks the given <code>wrapped-value</code> so that it will be passed unmodified to the browser.
    -  [`mark-preserve-keys`](#nextjournal.clerk/mark-preserve-keys) - Marks the given <code>wrapped-value</code> so that the keys will be passed unmodified to the browser.
    -  [`md`](#nextjournal.clerk/md) - Displays <code>x</code> with the markdown viewer.
    -  [`notebook`](#nextjournal.clerk/notebook) - Experimental notebook viewer.
    -  [`plotly`](#nextjournal.clerk/plotly) - Displays <code>x</code> with the plotly viewer.
    -  [`recompute!`](#nextjournal.clerk/recompute!) - Recomputes the currently visible doc, without parsing it.
    -  [`reset-viewers!`](#nextjournal.clerk/reset-viewers!) - Resets the viewers associated with the current <code>*ns*</code> to <code>viewers</code>.
    -  [`row`](#nextjournal.clerk/row) - Displays <code>xs</code> as rows.
    -  [`serve!`](#nextjournal.clerk/serve!) - Main entrypoint to Clerk taking an configurations map.
    -  [`set-viewers!`](#nextjournal.clerk/set-viewers!) - Deprecated, please use <code>add-viewers!</code> instead.
    -  [`show!`](#nextjournal.clerk/show!) - Evaluates the Clojure source in <code>file-or-ns</code> and makes Clerk show it in the browser.
    -  [`table`](#nextjournal.clerk/table) - Displays <code>xs</code> using the table viewer.
    -  [`tex`](#nextjournal.clerk/tex) - Displays <code>x</code> as LaTeX using KaTeX.
    -  [`update-val`](#nextjournal.clerk/update-val) - Take a function <code>f</code> and optional <code>args</code> and returns a function to update only the <code>:nextjournal/value</code> inside a wrapped-value.
    -  [`update-viewers`](#nextjournal.clerk/update-viewers) - Takes <code>viewers</code> and a <code>select-fn->update-fn</code> map returning updated viewers with each viewer matching <code>select-fn</code> will by updated using the function in <code>update-fn</code>.
    -  [`use-headers`](#nextjournal.clerk/use-headers) - Treats the first element of the seq <code>xs</code> as a header for the table.
    -  [`valuehash`](#nextjournal.clerk/valuehash)
    -  [`vl`](#nextjournal.clerk/vl) - Displays <code>x</code> with the vega embed viewer, supporting both vega-lite and vega.
    -  [`with-cache`](#nextjournal.clerk/with-cache) - An expression evaluated with Clerk's caching.
    -  [`with-viewer`](#nextjournal.clerk/with-viewer) - Displays <code>x</code> using the given <code>viewer</code>.
    -  [`with-viewers`](#nextjournal.clerk/with-viewers)

-----
# <a name="nextjournal.clerk">nextjournal.clerk</a>


Clerk's Public API.




## <a name="nextjournal.clerk/->value">`->value`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L161-L164)
<a name="nextjournal.clerk/->value"></a>
``` clojure

(->value x)
```


Takes `x` and returns the `:nextjournal/value` from it, or otherwise `x` unmodified.

## <a name="nextjournal.clerk/add-viewers">`add-viewers`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L131-L133)
<a name="nextjournal.clerk/add-viewers"></a>
``` clojure

(add-viewers added-viewers)
(add-viewers viewers added-viewers)
```


## <a name="nextjournal.clerk/add-viewers!">`add-viewers!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L148-L150)
<a name="nextjournal.clerk/add-viewers!"></a>
``` clojure

(add-viewers! viewers)
```


Adds `viewers` to the viewers associated with the current `*ns*`.

## <a name="nextjournal.clerk/build!">`build!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L429-L471)
<a name="nextjournal.clerk/build!"></a>
``` clojure

(build! build-opts)
```


Creates a static html build from a collection of notebooks.

  Options:
  - `:paths`     - a vector of relative paths to notebooks to include in the build
  - `:paths-fn`  - a symbol resolving to a 0-arity function returning computed paths
  - `:index`     - a string allowing to override the name of the index file, will be added to `:paths`

  Passing at least one of the above is required. When both `:paths`
  and `:paths-fn` are given, `:paths` takes precendence.

  - `:bundle`      - if true results in a single self-contained html file including inlined images
  - `:compile-css` - if true compiles css file containing only the used classes
  - `:ssr`         - if true runs react server-side-rendering and includes the generated markup in the html
  - `:browse`      - if true will open browser with the built file on success
  - `:dashboard`   - if true will start a server and show a rich build report in the browser (use with `:bundle` to open browser)
  - `:out-path`  - a relative path to a folder to contain the static pages (defaults to `"public/build"`)
  - `:git/sha`, `:git/url` - when both present, each page displays a link to `(str url "blob" sha path-to-notebook)`
  

## <a name="nextjournal.clerk/build-static-app!">`build-static-app!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L475-L477)
<a name="nextjournal.clerk/build-static-app!"></a>
``` clojure

(build-static-app! build-opts)
```


## <a name="nextjournal.clerk/caption">`caption`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L309-L312)
<a name="nextjournal.clerk/caption"></a>
``` clojure

(caption text content)
```


Displays `content` with `text` as caption below it.

## <a name="nextjournal.clerk/clear-cache!">`clear-cache!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L479-L502)
<a name="nextjournal.clerk/clear-cache!"></a>
``` clojure

(clear-cache!)
(clear-cache! sym-or-form)
```


Clears the in-memory and file-system caches when called with no arguments.

  Clears the cache for a single result identitfied by `sym-or-form` argument which can be:
  * a symbol representing the var name (qualified or not)
  * the form of an anonymous expression

## <a name="nextjournal.clerk/code">`code`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L314-L325)
<a name="nextjournal.clerk/code"></a>
``` clojure

(code code-string-or-form)
(code viewer-opts code-string-or-form)
```


Displays `x` as syntax highlighted Clojure code.

  A string is shown as-is, any other arg will be pretty-printed via `clojure.pprint/pprint`.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`

## <a name="nextjournal.clerk/col">`col`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L274-L284)
<a name="nextjournal.clerk/col"></a>
``` clojure

(col & xs)
```


Displays `xs` as columns.

  Treats the first argument as `viewer-opts` if it is a map but not a `wrapped-value?`.

  The `viewer-opts` map can contain the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`

## <a name="nextjournal.clerk/defcached">`defcached`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L517-L521)
<a name="nextjournal.clerk/defcached"></a>
``` clojure

(defcached name expr)
```


Macro.


Like `clojure.core/def` but with Clerk's caching of the value.

## <a name="nextjournal.clerk/doc-url">`doc-url`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L341-L341)
<a name="nextjournal.clerk/doc-url"></a>
``` clojure

(doc-url path)
```


## <a name="nextjournal.clerk/eval-cljs">`eval-cljs`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L332-L335)
<a name="nextjournal.clerk/eval-cljs"></a>
``` clojure

(eval-cljs & forms)
```


Evaluates the given ClojureScript forms in the browser.

## <a name="nextjournal.clerk/eval-cljs-str">`eval-cljs-str`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L327-L330)
<a name="nextjournal.clerk/eval-cljs-str"></a>
``` clojure

(eval-cljs-str code-string)
```


Evaluates the given ClojureScript `code-string` in the browser.

## <a name="nextjournal.clerk/example">`example`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L343-L350)
<a name="nextjournal.clerk/example"></a>
``` clojure

(example & body)
```


Macro.


Evaluates the expressions in `body` showing code next to results in Clerk.

  Does nothing outside of Clerk, like `clojure.core/comment`.

## <a name="nextjournal.clerk/file->viewer">`file->viewer`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L352-L355)
<a name="nextjournal.clerk/file->viewer"></a>
``` clojure

(file->viewer file)
(file->viewer opts file)
```


Evaluates the given `file` and returns it's viewer representation.

## <a name="nextjournal.clerk/get-default-viewers">`get-default-viewers`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L126-L129)
<a name="nextjournal.clerk/get-default-viewers"></a>
``` clojure

(get-default-viewers)
```


Gets Clerk's default viewers.

## <a name="nextjournal.clerk/halt!">`halt!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L416-L420)
<a name="nextjournal.clerk/halt!"></a>
``` clojure

(halt!)
```


Stops the Clerk webserver and file watcher.

## <a name="nextjournal.clerk/halt-watcher!">`halt-watcher!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L359-L365)
<a name="nextjournal.clerk/halt-watcher!"></a>
``` clojure

(halt-watcher!)
```


Halts the filesystem watcher when active.

## <a name="nextjournal.clerk/hide-result">`hide-result`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L297-L301)
<a name="nextjournal.clerk/hide-result"></a>
``` clojure

(hide-result x)
(hide-result viewer-opts x)
```


Deprecated, please put `^{:nextjournal.clerk/visibility {:result :hide}}` metadata on the form instead.

## <a name="nextjournal.clerk/html">`html`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L189-L198)
<a name="nextjournal.clerk/html"></a>
``` clojure

(html x)
(html viewer-opts x)
```


Displays `x` using the html-viewer. Supports HTML and SVG as strings or hiccup.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`

## <a name="nextjournal.clerk/image">`image`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L303-L307)
<a name="nextjournal.clerk/image"></a>
``` clojure

(image url)
(image viewer-opts url)
```


Creates a `java.awt.image.BufferedImage` from `url`, which can be a `java.net.URL` or a string, and
  displays it using the `buffered-image-viewer`.

## <a name="nextjournal.clerk/mark-presented">`mark-presented`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L173-L176)
<a name="nextjournal.clerk/mark-presented"></a>
``` clojure

(mark-presented wrapped-value)
```


Marks the given `wrapped-value` so that it will be passed unmodified to the browser.

## <a name="nextjournal.clerk/mark-preserve-keys">`mark-preserve-keys`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L179-L182)
<a name="nextjournal.clerk/mark-preserve-keys"></a>
``` clojure

(mark-preserve-keys wrapped-value)
```


Marks the given `wrapped-value` so that the keys will be passed unmodified to the browser.

## <a name="nextjournal.clerk/md">`md`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L200-L209)
<a name="nextjournal.clerk/md"></a>
``` clojure

(md x)
(md viewer-opts x)
```


Displays `x` with the markdown viewer.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`

## <a name="nextjournal.clerk/notebook">`notebook`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L337-L339)
<a name="nextjournal.clerk/notebook"></a>

Experimental notebook viewer. You probably should not use this.

## <a name="nextjournal.clerk/plotly">`plotly`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L211-L220)
<a name="nextjournal.clerk/plotly"></a>
``` clojure

(plotly x)
(plotly viewer-opts x)
```


Displays `x` with the plotly viewer.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`

## <a name="nextjournal.clerk/recompute!">`recompute!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L70-L76)
<a name="nextjournal.clerk/recompute!"></a>
``` clojure

(recompute!)
```


Recomputes the currently visible doc, without parsing it.

## <a name="nextjournal.clerk/reset-viewers!">`reset-viewers!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L143-L145)
<a name="nextjournal.clerk/reset-viewers!"></a>
``` clojure

(reset-viewers! viewers)
```


Resets the viewers associated with the current `*ns*` to `viewers`.

## <a name="nextjournal.clerk/row">`row`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L262-L272)
<a name="nextjournal.clerk/row"></a>
``` clojure

(row & xs)
```


Displays `xs` as rows.

  Treats the first argument as `viewer-opts` if it is a map but not a `wrapped-value?`.

  The `viewer-opts` map can contain the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`

## <a name="nextjournal.clerk/serve!">`serve!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L374-L412)
<a name="nextjournal.clerk/serve!"></a>
``` clojure

(serve! config)
```


Main entrypoint to Clerk taking an configurations map.

  Will obey the following optional configuration entries:

  * a `:port` for the webserver to listen on, defaulting to `7777`
  * `:browse?` will open Clerk in a browser after it's been started
  * a sequence of `:watch-paths` that Clerk will watch for file system events and show any changed file
  * a `:show-filter-fn` to restrict when to re-evaluate or show a notebook as a result of file system event. Useful for e.g. pinning a notebook. Will be called with the string path of the changed file.

  Can be called multiple times and Clerk will happily serve you according to the latest config.

## <a name="nextjournal.clerk/set-viewers!">`set-viewers!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L153-L158)
<a name="nextjournal.clerk/set-viewers!"></a>
``` clojure

(set-viewers! viewers)
```


Deprecated, please use [`add-viewers!`](#nextjournal.clerk/add-viewers!) instead.

## <a name="nextjournal.clerk/show!">`show!`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L23-L63)
<a name="nextjournal.clerk/show!"></a>
``` clojure

(show! file-or-ns)
```


Evaluates the Clojure source in `file-or-ns` and makes Clerk show it in the browser.

  Accepts ns using a quoted symbol or a `clojure.lang.Namespace`, calls `slurp` on all other arguments, e.g.:

  (nextjournal.clerk/show! "notebooks/vega.clj")
  (nextjournal.clerk/show! 'nextjournal.clerk.tap)
  (nextjournal.clerk/show! (find-ns 'nextjournal.clerk.tap))
  (nextjournal.clerk/show! "https://raw.githubusercontent.com/nextjournal/clerk-demo/main/notebooks/rule_30.clj")
  (nextjournal.clerk/show! (java.io.StringReader. ";; # Notebook from String 👋
(+ 41 1)"))
  

## <a name="nextjournal.clerk/table">`table`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L244-L260)
<a name="nextjournal.clerk/table"></a>
``` clojure

(table xs)
(table viewer-opts xs)
```


Displays `xs` using the table viewer.

  Performs normalization on the data, supporting:
  * seqs of maps
  * maps of seqs
  * seqs of seqs

  If you want a header for seqs of seqs use [`use-headers`](#nextjournal.clerk/use-headers).

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`

## <a name="nextjournal.clerk/tex">`tex`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L286-L295)
<a name="nextjournal.clerk/tex"></a>
``` clojure

(tex x)
(tex viewer-opts x)
```


Displays `x` as LaTeX using KaTeX.

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`

## <a name="nextjournal.clerk/update-val">`update-val`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L167-L170)
<a name="nextjournal.clerk/update-val"></a>
``` clojure

(update-val f & args)
```


Take a function `f` and optional `args` and returns a function to update only the `:nextjournal/value` inside a wrapped-value.

## <a name="nextjournal.clerk/update-viewers">`update-viewers`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L135-L140)
<a name="nextjournal.clerk/update-viewers"></a>
``` clojure

(update-viewers viewers select-fn->update-fn)
```


Takes `viewers` and a `select-fn->update-fn` map returning updated
  viewers with each viewer matching `select-fn` will by updated using
  the function in `update-fn`.

## <a name="nextjournal.clerk/use-headers">`use-headers`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L237-L242)
<a name="nextjournal.clerk/use-headers"></a>
``` clojure

(use-headers xs)
```


Treats the first element of the seq `xs` as a header for the table.

  Meant to be used in combination with [`table`](#nextjournal.clerk/table).

## <a name="nextjournal.clerk/valuehash">`valuehash`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L427-L427)
<a name="nextjournal.clerk/valuehash"></a>

## <a name="nextjournal.clerk/vl">`vl`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L222-L235)
<a name="nextjournal.clerk/vl"></a>
``` clojure

(vl x)
(vl viewer-opts x)
```


Displays `x` with the vega embed viewer, supporting both vega-lite and vega.

  `x` should be the standard vega view description map, accepting the following addtional keys:
  * `:embed/callback` a function to be called on the vega-embed object after creation.
  * `:embed/opts` a map of vega-embed options (see https://github.com/vega/vega-embed#options)

  Supports an optional first `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`

## <a name="nextjournal.clerk/with-cache">`with-cache`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L509-L513)
<a name="nextjournal.clerk/with-cache"></a>
``` clojure

(with-cache form)
```


Macro.


An expression evaluated with Clerk's caching.

## <a name="nextjournal.clerk/with-viewer">`with-viewer`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L110-L119)
<a name="nextjournal.clerk/with-viewer"></a>
``` clojure

(with-viewer viewer x)
(with-viewer viewer viewer-opts x)
```


Displays `x` using the given `viewer`.

  Takes an optional second `viewer-opts` map arg with the following optional keys:

  * `:nextjournal.clerk/width`: set the width to `:full`, `:wide`, `:prose`
  * `:nextjournal.clerk/viewers`: a seq of viewers to use for presentation of this value and its children
  * `:nextjournal.clerk/opts`: a map argument that will be passed to the viewers `:render-fn`

## <a name="nextjournal.clerk/with-viewers">`with-viewers`</a> [📃](https://github.com/nextjournal/clerk/blob/main/src/nextjournal/clerk.clj#L122-L124)
<a name="nextjournal.clerk/with-viewers"></a>
``` clojure

(with-viewers viewers x)
```

