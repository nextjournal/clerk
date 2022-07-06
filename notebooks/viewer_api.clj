;; # 👁 Clerk Viewer API
;; Clerk comes with a moldable viewer api that is open.
^{:nextjournal.clerk/toc true}
(ns notebook.viewer-api
  "a Notebook with usage examples for Clerk's viewer API"
  (:require [nextjournal.clerk :as clerk]))

;; ## 🧩 Built-in Viewers
;; The default set of viewers are able to render Clojure data.
{:hello "world 👋" :tacos (map (comp #(map (constantly '🌮) %) range) (range 1 100)) :zeta {:chars [\w \a \v \e] :set (set (range 100))}}

;; And can handle lazy infinte sequences, only partially loading data by default with the ability to load more data on request.
(range)

(def fib (lazy-cat [0 1] (map + fib (rest fib))))

;; In addition, there's a number of built-in viewers.
;; ### 🕸 Hiccup
;; The `html` viewer interprets `hiccup` when passed a vector.
(clerk/html [:div "As Clojurians we " [:em "really"] " enjoy hiccup"])

;; Alternatively you can pass it an HTML string.
(clerk/html "Never <strong>forget</strong>.")

;; ### 🔢 Tables
;; The table viewer api take a number of formats. Each viewer also takes an optional map as a first argument for customization.
(clerk/table {::clerk/width :full} (into (sorted-map) (map (fn [c] [(keyword (str c)) (shuffle (range 5))])) "abcdefghiklmno"))

;; ### 📑 Markdown
;; The Markdown viewer is useful for programmatically generated markdown.
(clerk/md (clojure.string/join "\n" (map #(str "* Item " (inc %)) (range 3))))


;; ### 🧮 TeX
;; The TeX viewer is built on [KaTeX](https://katex.org/).
(clerk/tex "f^{\\circ n} = \\underbrace{f \\circ f \\circ \\cdots \\circ f}_{n\\text{ times}}.\\,")


;; ### 📊 Plotly
(clerk/plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]})

;; ### 📈 Vega Lite
(clerk/vl {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                         :format {:type "topojson" :feature "counties"}}
           :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                            :key "id" :fields ["rate"]}}]
           :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

;; ### 👾 Code
;; The code viewer uses [clojure-mode](https://nextjournal.github.io/clojure-mode/) for syntax highlighting.
(clerk/code (macroexpand '(when test
                            expression-1
                            expression-2)))

(clerk/code '(ns foo "A great ns" (:require [clojure.string :as str])))

(clerk/code "(defn my-fn\n  \"This is a Doc String\"\n  [args]\n  42)")

;; ### 🔤 Strings
;; Multi-line strings can be expanded to break on newlines.
(do "The\npurpose\nof\nvisualization\nis\ninsight,\nnot\npictures.")

;; ## 🚀 Extensibility
(clerk/with-viewer '#(v/html [:div "Greetings to " [:strong %] "!"])
  "James Clerk Maxwell")

^{::clerk/viewer {:render-fn '#(v/html [:span "The answer is " % "."])
                  :transform-fn (comp inc :nextjournal/value)}}
(do 41)

(clerk/with-viewers (clerk/add-viewers [{:pred number?
                                         :render-fn '(fn [n] (v/html [:div.inline-block [(keyword (str "h" n)) (str "Heading " n)]]))}])
  [1 2 3 4 5])

^::clerk/no-cache
(clerk/with-viewers (clerk/add-viewers [{:pred number? :render-fn '#(v/html [:div.inline-block {:style {:width 16 :height 16}
                                                                                                :class (if (pos? %) "bg-black" "bg-white border-solid border-2 border-black")}])}])
  (take 10 (repeatedly #(rand-int 2))))

^{::clerk/viewers
  (clerk/add-viewers [{:pred #(and (string? %)
                                   (re-matches
                                    (re-pattern
                                     (str "(?i)"
                                          "(#(?:[0-9a-f]{2}){2,4}|(#[0-9a-f]{3})|"
                                          "(rgb|hsl)a?\\((-?\\d+%?[,\\s]+){2,3}\\s*[\\d\\.]+%?\\))")) %))
                       :render-fn '#(v/html [:div.inline-block.rounded-sm.shadow
                                             {:style {:width 16
                                                      :height 16
                                                      :border "1px solid rgba(0,0,0,.2)"
                                                      :background-color %}}])}])}
["#571845"
 "rgb(144,12,62)"
 "rgba(199,0,57,1.0)"
 "hsl(11,100%,60%)"
 "hsla(46, 97%, 48%, 1.000)"]


;; The clerk viewer api also includes `reagent` and `applied-science/js-interop`.
(clerk/with-viewer '(fn [_]
                      (reagent/with-let [counter (reagent/atom 0)]
                        (v/html [:h3.cursor-pointer {:on-click #(swap! counter inc)} "I was clicked " @counter " times."])))
  nil)
