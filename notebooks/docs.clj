;; # Documentation
^{:nextjournal.clerk/toc true}
(ns docs
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.analyzer :as ana]
            [nextjournal.clerk.viewer :as v]
            [weavejester.dependency :as dep]))

;; ## How Clerk Works
;; ### File Watching
;; The interface to Clerk is really simple: you save a Clojure file and Clerk takes care of turning that into a notebook.
;; The file watcher library we're using is beholder, originally written by David Nolen for Krell.

;; ### Evaluation
;; #### Step 1: Parsing
;; First, we parse a given Clojure file using `rewrite-clj`.
(def parsed
  (parser/parse-file "notebooks/docs.clj"))

;; #### Step 2: Analysis
;; Then, each expression is analysed using `tools.analyzer`. A dependency graph, the analyzed form and the originating file is recorded.

(def analyzed
  (ana/build-graph parsed))


;; This analysis is done recursively, descending into all dependency symbols.

(ana/find-location 'nextjournal.clerk.analyzer/analyze-file)

(ana/find-location `dep/depend)

(ana/find-location 'io.methvin.watcher.DirectoryChangeEvent)

(ana/find-location 'java.util.UUID)


(let [{:keys [graph]} analyzed]
  (dep/transitive-dependencies graph 'how-clerk-works/analyzed))

;; #### Step 3: Analyzer
;; Then we can use this information to hash each expression.
(def hashes
  (ana/hash analyzed))

;; #### Step 4: Evaluation
;; Clerk uses the hashes as filenames and only re-evaluates forms that haven't been seen before. The cache is using [nippy](https://github.com/ptaoussanis/nippy).
(def rand-fifteen
  (do (Thread/sleep 10)
      (shuffle (range 15))))

;; We can look up the cache key using the var name in the hashes map.
(when-let [form-hash (get hashes `rand-fifteen)]
  (let [hash (slurp (eval/->cache-file (str "@" form-hash)))]
    (eval/thaw-from-cas hash)))

;; As an escape hatch, you can tag a form or var with `::clerk/no-cache` to always re-evaluate it. The following form will never be cached.
^::clerk/no-cache (shuffle (range 42))

;; For side effectful functions that should be cached, like a database query, you can add a value like this `#inst` to control when evaluation should happen.

(def query-results
  (let [_run-at #_(java.util.Date.) #inst "2021-05-20T08:28:29.445-00:00"
        ds (next.jdbc/get-datasource {:dbtype "sqlite" :dbname "chinook.db"})]
    (with-open [conn (next.jdbc/get-connection ds)]
      (clerk/table (next.jdbc/execute! conn ["SELECT AlbumId, Bytes, Name, TrackID, UnitPrice FROM tracks"])))))

;; ## Viewers

;; Clerk comes with a moldable viewer api that is open.

;; ### Built-in Viewers
;; The default set of viewers are able to render Clojure data.
{:hello "world 👋" :tacos (map (comp #(map (constantly '🌮) %) range) (range 1 100)) :zeta {:chars [\w \a \v \e] :set (set (range 100))}}

;; And can handle lazy infinte sequences, only partially loading data by default with the ability to load more data on request.
(range)

(def fib (lazy-cat [0 1] (map + fib (rest fib))))

;; In addition, there's a number of built-in viewers.
;; #### Hiccup
;; The `html` viewer interprets `hiccup` when passed a vector.
(clerk/html [:div "As Clojurians we " [:em "really"] " enjoy hiccup"])

;; Alternatively you can pass it an HTML string.
(clerk/html "Never <strong>forget</strong>.")

;; #### Tables
;; The table viewer api take a number of formats. Each viewer also takes an optional map as a first argument for customization.
(clerk/table {::clerk/width :full} (into (sorted-map) (map (fn [c] [(keyword (str c)) (shuffle (range 5))])) "abcdefghiklmno"))

;; #### Markdown
;; The Markdown viewer is useful for programmatically generated markdown.
(clerk/md (clojure.string/join "\n" (map #(str "* Item " (inc %)) (range 3))))


;; #### TeX
;; The TeX viewer is built on [KaTeX](https://katex.org/).
(clerk/tex "f^{\\circ n} = \\underbrace{f \\circ f \\circ \\cdots \\circ f}_{n\\text{ times}}.\\,")


;; #### Plotly
(clerk/plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]})

;; #### Vega Lite
(clerk/vl {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                         :format {:type "topojson" :feature "counties"}}
           :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                            :key "id" :fields ["rate"]}}]
           :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

;; #### Code
;; The code viewer uses [clojure-mode](https://nextjournal.github.io/clojure-mode/) for syntax highlighting.
(clerk/code (macroexpand '(when test
                            expression-1
                            expression-2)))

(clerk/code '(ns foo "A great ns" (:require [clojure.string :as str])))

(clerk/code "(defn my-fn\n  \"This is a Doc String\"\n  [args]\n  42)")

;; #### Strings
;; Multi-line strings can be expanded to break on newlines.
(do "The\npurpose\nof\nvisualization\nis\ninsight,\nnot\npictures.")

;; ### Extensibility & Customization

;; Our goal with the Clerk viewer api is to _keep the toolbox open_
;; and let folks change both how things are displayed as well as how things behave. In this notebook, we'll go
;; through how the viewer api works and how you can change it.

;; Clerk comes with a rich set of default viewers, and this is them.
v/default-viewers

;; A Clerk viewer is just a Clojure map. Let's start with a very basic example.
(def greeting-viewer
  {:render-fn '(fn [name] (v/html [:strong "Hello, " name "!"]))})

;; In it's simplest form, a viewer has just a `:render-fn`. Notice that the value is not yet a function, but a quoted form that will be sent via a websocket to the browser. There, it will be evaluated using the [Small Clojure Intepreter](https://github.com/babashka/sci) or SCI for short. Let's use the viewer to confirm it does what we expect:
(v/with-viewer greeting-viewer
  "James Clerk Maxwell")

;; It is often useful, but not a neccesity to define a viewer in a clojure var, so the following expression yields the same result.
(v/with-viewer {:render-fn '(fn [name] (v/html [:strong "Hello, " name "!"]))}
  "James Clerk Maxwell")

;; Besides `:render-fn`, there's also a part of the viewer api, that runs directly in JVM Clojure, `:transform-fn`.
;; We can use it do archieve the same thing:
(v/with-viewer {:transform-fn (fn [wrapped-value]
                                (v/html [:strong "Hello, " (v/->value wrapped-value) "!"]))}
  "James Clerk Maxwell")

;; Note that this _is_ a function, not a quoted form like `:render-fn`. It does not recieve the plain value, but it's value is wrapped in a map under the `:nextjournal/value` key which allows it to carry and convey additional information.

;; Let's use `v/apply-viewers` to look more closely at what this does:
#_ "TODO: remove equality hack once we can display wrapped-values as-is." 
(= (-> (v/with-viewer {:transform-fn (fn [wrapped-value]
                                       (v/html [:strong "Hello, " (v/->value wrapped-value) "!"]))}
         "James Clerk Maxwell")
       (v/apply-viewers)
       (v/process-wrapped-value))
   {:nextjournal/viewer {:name :html, :render-fn (v/->ViewerFn 'v/html)},
    :nextjournal/value [:strong "Hello, " "James Clerk Maxwell" "!"]})

;; Without a viewer specified, Clerk will go through the a sequence viewers and apply the `:pred` function in the viewer to find a matching one. Use `v/viewer-for` to select a viewer for a given value.
(def char?-viewer
  (v/viewer-for v/default-viewers \A))

(def string?-viewer
  (v/viewer-for v/default-viewers "Denn wir sind wie Baumstämme im Schnee."))

;; Notice that for the `string?` viewer above, there's a `{:n 80}` on there. This is the case for all collection viewers in Clerk and controls how many elements are displayed. So using the default `string?-viewer` above, we're showing the first 80 characters.
(def long-string
  (str/join (into [] cat (repeat 10 (range 10)))))

;; If we change the viewer and set a different `:n` in `:fetch-opts`, we only see 10 characters.
(v/with-viewer (assoc-in string?-viewer [:fetch-opts :n] 10)
  long-string)

;; Or, we can turn off eliding, by dissoc'ing `:fetch-opts` alltogether.
(v/with-viewer (dissoc string?-viewer :fetch-opts)
  long-string)

;; The operations above were changes to a single viewer. But we also have a function `update-viewers` to update a given viewers by applying a `select-fn->update-fn` map. Here, the predicate is the keyword `:fetch-opts` and our update function is called for every viewer with `:fetch-opts` and is dissoc'ing them.
(def without-pagination
  {:fetch-opts #(dissoc % :fetch-opts)})

;; Here's the updated-viewers:
(def viewers-without-lazy-loading
  (v/update-viewers v/default-viewers without-pagination))

;; Now let's confirm these modified viewers don't have `:fetch-opts` on them anymore.
(filter :fetch-opts viewers-without-lazy-loading)

;; And compare it with the defaults:
(filter :fetch-opts v/default-viewers)

;; Here are some more examples:

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

;; ### Controlling Visibility
;; You can control visibility in Clerk by setting the `:nextjournal.clerk/visibility` which takes a map with keys `:code` and `:result` to control the visibility for the code cells and its results.
;; 
;; Valid values are `:show`, `:hide` and `:fold` (only valid for code cells).
;; A declaration on the `ns` metadata map lets all code cells in the notebook inherit the value.

;; So a cell will only show the result now while you can uncollapse the code cell.
(+ 39 3)

;; If you want, you can override it. So the following cell is shown:
^{::clerk/visibility {:code :show}} (range 25)

;; While this one is completely hidden, without the ability to uncollapse it.
^{::clerk/visibility {:code :hide}} (shuffle (range 25))

;; When you'd like to hide the result of a cell, set `::clerk/visibility` should contain `{:result :hide}`.
^{::clerk/visibility {:code :show :result :hide}}
(def my-range (range 500))

(rand-int 42)

;; You can change the defaults applied to the document uing a top-level map with `:nextjournal.clerk/visibility` key, so the code cells below this marker will all be shown.
{:nextjournal.clerk/visibility {:code :show}}

(rand-int (inc 41))

;; Further work:
;; * [x] support setting and changing the defaults for parts of a doc using `{::clerk/visibility {:code :show}}` top-level forms.
;; * [ ] remove the `::clerk/visibility` metadata from the displayed code cells to not distract from the essence.
