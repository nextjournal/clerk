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
```

and parse it into a nested clojure structure

```clojure
(def parsed (md/parse markdown-input))
```

which you can manipulate with your favourite clojure functions

```clojure
(def sliced (update parsed :content #(take 8 %)))
```

and render back to hiccup with customisable elements. 

At present, Clerk will split top level forms which are grouped togetehr under the same cell, this is to guarantee that Clerk's dependency analysys among forms will still effectively avoid needless recomputations when code changes. Forms are nevertheless still grouped as intended in the document.

```clojure
(def renderers 
  (assoc md.transform/default-hiccup-renderers 
        :doc (partial md.transform/into-markup [:div.viewer-markdown])
        :ruler (fn [_ _]
                 [:hr.mt-1.mb-1
                  {:style {:border "10px solid magenta" 
                           :border-radius "10px"}}])))

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
