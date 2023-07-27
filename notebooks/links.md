# Links
```clojure
(ns links
  {:nextjournal.clerk/toc true}
  (:require [nextjournal.clerk :as clerk]))
```
## Design

We have three different modes to consider for links:

1. Interactive mode `serve!`
2. Static build unbundled `(build! {:bundle false})`
3. Static build bundled `(build! {:bundle true})`

The behaviour when triggering links we want is:

1. Interactive mode: trigger a js event `(clerk-eval 'nextjournal.clerk.webserver/navigate! ,,,)`, the doc will in turn be updated via the websocket
2. Static build unbundled: not intercept the link, let the browser perform its normal navigation
3. Static build bundled: trigger a js event to update the doc, update the browser's hash so the doc state is persisted on reload

We can allow folks to write normal (relative) links. The limitations here being that things like open in new tab would not work and we can't support a routing function. Both these limitations means we probably want to continue encouraging the use of a helper like `clerk/doc-url` going forward.

We currently don't support navigating to headings / table of contents sections in the bundled build. This could be supported however by introducing a way to encode that in the hash e.g. with `#page:section`.


## Examples


### JVM-Side

The helper `clerk/doc-url` allows to reference notebooks by path. We currently support relative paths with respect to the directory which started the Clerk application. An optional trailing hash fragment can appended to the path in order for the page to be scrolled up to the indicated identifier.


```clojure
(clerk/html
 [:ol.bg-lime-100
  [:li [:a {:href (clerk/doc-url 'nextjournal.clerk.home)} "Home"]]
  [:li [:a {:href (clerk/doc-url "notebooks/viewers/html")} "HTML"]]
  [:li [:a {:href (clerk/doc-url "notebooks/viewers/image")} "Images"]]
  [:li [:a {:href (clerk/doc-url "notebooks/markdown.md" "appendix")} "Markdown / Appendix"]]
  [:li [:a {:href (clerk/doc-url "notebooks/how_clerk_works" "step-3:-analyzer")} "Clerk Analyzer"]]
  [:li [:a {:href (clerk/doc-url "book")} "The 📕Book"]]
  [:li [:a {:href (clerk/doc-url "")} "Homepage"]]])
```

### Render

The same functionality is available in the SCI context when building render functions.

```clojure
(clerk/with-viewer
  '(fn [_ _]
     [:ol
      [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/viewers/html")} "HTML"]]
      [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/markdown")} "Markdown"]]
      [:li [:a {:href (nextjournal.clerk.viewer/doc-url "notebooks/viewer_api")} "Viewer API / Tables"]]]) nil)

```


### Inside Markdown

Links should work inside markdown as well.

* [HTML](../notebooks/viewers/html) (relative link)
* [HTML](clerk/doc-url,"notebooks/viewers/html") (doc url, currently not functional)
