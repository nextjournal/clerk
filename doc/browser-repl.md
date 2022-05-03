# Browser REPL

Clerk supports connecting to an nREPL server that runs directly in the browser, for developing viewer functions.

## Usage

In your JVM REPL, execute the following:

``` clojure
(require '[nextjournal.clerk :as clerk])
(clerk/serve! {})
(require '[nextjournal.clerk.browser-nrepl :as bnrepl])
(bnrepl/start-browser-nrepl! {:port 1339})
(clerk/show! "notebooks/cljs_render_fn_file.clj")
```

The `notebooks/cljs_render_fn_file.clj` notebook is part of the Clerk repo. It
shows how you can load a viewer function from a `.cljs` file `cljs_render_fn_source.cljs`.

View a notebook in your browser at https://localhost:7777.
Your notebook's JS will connect to a browser nREPL server running on port 1339.

### CIDER

To connect to the browser nREPL client from CIDER, insert this snippet in your emacs config:

``` elisp
(cider-register-cljs-repl-type 'clerk-browser-repl "(+ 42)")

(defun mm/cider-connected-hook ()
  (when (eq 'nbb cider-cljs-repl-type)
    (setq-local cider-show-error-buffer nil)
    (cider-set-repl-type 'cljs)))

(add-hook 'cider-connected-hook #'mm/cider-connected-hook)
```

Now enter the `cljs_render_fn_source.cljs` file and choose:

- `cider-connect-cljs`, `localhost`, `1339`, `clerk-browser-repl`

Now you should be able to evaluate your viewer functionm interactively.  In your
viewer cljs file, you can require `[nextjournal.clerk.sci-viewer :as v]` and
call `v/mount` re-mount React components.
