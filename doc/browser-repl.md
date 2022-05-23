# Browser REPL

Clerk supports connecting to an nREPL server that runs directly in the browser, for developing viewer functions.

## Usage

In `deps.edn`, in your `:deps` or `:clerk` alias, add the following dependency,
in addition to clerk itself:

``` clojure
io.github.babashka/sci.nrepl {:git/sha "<latest-sha>"}
```

Check [here](https://github.com/babashka/sci.nrepl) for the latest SHA.

When starting your JVM, set the `clerk.sci_nrepl` property:

``` clojure
clj -J-Dclerk.sci_nrepl='{}' ...
```

or add it to `:jvm-opts` in your `:clerk` alias (top level jvm opts are not
supported in `deps.edn`!).


The property contains the following configuration options and defaults:

``` clojure
{:nrepl-port 1339 :websocket-port 1340}
```

As an example we take the notebook `notebooks/cljs_render_fn_file.clj` which
loads a ClojureScript file from the classpath, `cljs_render_fn_source.cljs`:

``` clojure
(require '[nextjournal.clerk :as clerk])
(clerk/serve! {})
(clerk/show! "notebooks/cljs_render_fn_file.clj")
```

View the notebook in your browser at https://localhost:7777.  The notebook in
the browser will connect back to `:websocket-port` which defaults to
`1340`. This websocket communicates with an nREPL server which is running by
default on port `1339`.

Open the `cljs_render_fn_source.cljs` file in your editor. You can now make an
nREPL connection to port `1339`. Read editor-specific instructions below.

### Calva

 In Calva choose `Connect
to a running REPL in your project` followed by `ClojureScript nREPL server`.

### CIDER

To connect to the browser nREPL client from CIDER, insert this snippet in your emacs config:

``` elisp
(cider-register-cljs-repl-type 'clerk-browser-repl "(+ 42)")

(defun mm/cider-connected-hook ()
  (when (eq 'clerk-browser-repl cider-cljs-repl-type)
    (setq-local cider-show-error-buffer nil)
    (cider-set-repl-type 'cljs)))

(add-hook 'cider-connected-hook #'mm/cider-connected-hook)
```

Now enter the `cljs_render_fn_source.cljs` file and choose:

- `cider-connect-cljs`, `localhost`, `1339`, `clerk-browser-repl`

Now you should be able to evaluate your viewer functionm interactively.  In your
viewer cljs file, you can require `[nextjournal.clerk.sci-viewer :as v]` and
call `v/mount` re-mount React components.
