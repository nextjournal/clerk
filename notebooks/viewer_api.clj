;; # 👁 Clerk Viewer API
;; Clerk comes with a moldable viewer api that is open.

(ns notebook.viewer-api
  (:require [nextjournal.clerk.viewer :as v]))

;; ## 🧩 Built-in Viewers
;; The default set of viewers are able to render Clojure data.
{:hello "world 👋" :num [1 2 3]}



;; And can handle lazy infinte sequences, only partially loading data by default with the ability to load more data on request.
(range)

(def fib (lazy-cat [0 1] (map + fib (rest fib))))

;; In addition, there's a number of built-in viewers.
;; ### 🕸 Hiccup
;; The `html` viewer interprets `hiccup` when passed a vector.
(v/html [:div "As Clojurians we " [:em "really"] " enjoy hiccup"])

;; Alternatively you can pass it an HTML string.
(v/html "Never <strong>forget</strong>.")


;; ### 📑 Markdown
;; The Markdown viewer is useful for programmatically generated markdown.
(v/md (clojure.string/join "\n" (map #(str "* Item " (inc %)) (range 3))))


;; ### 🧮 TeX
;; The TeX viewer is built on [KaTeX](https://katex.org/).
(v/tex "f^{\\circ n} = \\underbrace{f \\circ f \\circ \\cdots \\circ f}_{n\\text{ times}}.\\,")


;; ### 📊 Plotly
(v/plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]})

;; ### 📈 Vega Lite
(v/vl {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                     :format {:type "topojson" :feature "counties"}}
       :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                        :key "id" :fields ["rate"]}}]
       :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

;; ### 👾 Code
;; The code viewer uses [clojure-mode](https://nextjournal.github.io/clojure-mode/) for syntax highlighting.
(v/code (macroexpand '(when test
                        expression-1
                        expression-2)))

;; ## 🚀 Extensibility

(v/with-viewer (fn [name] (v/html [:div "Greetings to " [:strong name] "!"])) "James Maxwell Clerk")


^:clerk/no-cache
(v/with-viewers [{:pred 'number? :fn #(v/html [:div.inline-block {:style {:width 16 :height 16}
                                                                  :class (if (pos? %) "bg-black" "bg-white border-solid border-2 border-black")}])}]
  (take 10 (repeatedly #(rand-int 2))))

(v/with-viewers
  [{:pred #(and (string? %)
                (re-matches
                 (re-pattern
                  (str "(?i)"
                       "(#(?:[0-9a-f]{2}){2,4}|(#[0-9a-f]{3})|"
                       "(rgb|hsl)a?\\((-?\\d+%?[,\\s]+){2,3}\\s*[\\d\\.]+%?\\))")) %))
    :fn #(v/html [:div.inline-block.rounded-sm.shadow
                  {:style {:width 16
                           :height 16
                           :border "1px solid rgba(0,0,0,.2)"
                           :background-color %}}])}]
  ["#571845"
   "rgb(144,12,62)"
   "rgba(199,0,57,1.0)"
   "hsl(11,100%,60%)"
   "hsla(46, 97%, 48%, 1.000)"])

(re-matches
  (re-pattern
    (str "(?i)"
         "(#(?:[0-9a-f]{2}){2,4}|(#[0-9a-f]{3})|"
         "(rgb|hsl)a?\\((-?\\d+%?[,\\s]+){2,3}\\s*[\\d\\.]+%?\\))"))
  "hsla(46, 97%, 48%, 1.000)")

#_(nextjournal.clerk/show! "notebooks/viewer_api.clj")
