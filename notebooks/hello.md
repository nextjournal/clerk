# Hello Markdown 👋

Clerk enables a _rich_, local-first notebook experience using standard
Clojure namespaces and markdown.

```clojure
(ns hello-markdown
  {:nextjournal.clerk/no-cache true
   :nextjournal.clerk/visibility {:code :fold}}
  (:require [nextjournal.clerk :as clerk]))
```

Here's a visualization of unemployment in the US.

```clojure
^{::clerk/viewer clerk/vl}
{:width 700 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
 :format {:type "topojson" :feature "counties"}}
 :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
 :key "id" :fields ["rate"]}}]
 :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}}
```
