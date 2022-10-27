# $\mathfrak{M}\!⬇$ Markdown Ingestion

This notebook demoes feeding Clerk with markdown files. We currently make no assumption on the kind of source code passed in fenced blocks, we handle code as if it were clojure. Indented code blocks are treated as inert code blocks.  

```clj
^:nextjournal.clerk/no-cache
(ns markdown-example
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))
```

Nextjournal Markdown library is able to ingest a markdown string

```clojure
(def markdown-input (slurp "https://daringfireball.net/projects/markdown/syntax.text"))


;; and parse it into a _nested clojure structure_ (an AST)


(def parsed (md/parse markdown-input))
```

At present, Clerk will split top level forms which are grouped together under the same cell, this is to guarantee that Clerk's dependency analysys among forms will still effectively avoid needless recomputations when code changes.

which you can manipulate with your favourite clojure functions

```clojure
(def sliced (update parsed :content #(take 8 %))) ;; take just a slice 
```

and render back to hiccup with customisable elements. 

```clojure
(def renderers 
  (assoc md.transform/default-hiccup-renderers 
        :doc (partial md.transform/into-markup [:div.viewer-markdown])
        :ruler (constantly [:hr.mt-1.mb-10.border-0.w-full.h-5.bg-fuchsia-900.rounded-full])))

(def hiccup 
  (md.transform/->hiccup renderers sliced))
```

and finally render via Clerk's `html` helper.

```clojure
^{::clerk/visibility :fold}
(clerk/html hiccup)
```

## Appendix

Don't forget the closing slice 🍕 of markdown!
