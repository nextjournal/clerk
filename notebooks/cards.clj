;; # 🃏 CLJS Cards
^{:nextjournal.clerk/toc true}
(ns cards
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [devcards :as dc]))

;; ## $\LaTeX$
(dc/defcard
  (v/tex "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}"))

;; ## Vega Lite
(dc/defcard
  (v/vl {:width 650
         :height 400
         :data
         {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
          :format
          {:type "topojson" :feature "counties"}}
         :transform
         [{:lookup "id"
           :from
           {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
            :key "id"
            :fields ["rate"]}}]
         :projection {:type "albersUsa"}
         :mark "geoshape"
         :encoding
         {:color {:field "rate" :type "quantitative"}}}))

;; ## Tables
(dc/defcard
  (v/table {:a [1 2 3] :b [4 5 6]}))

(dc/defcard
  (v/table {:head ['A 'B 'C]
            :rows (map #(range 3) (range 2))}))

;; incomplete tables
(dc/defcard
  (v/table {:a [1 2 3] :b [4]}))

;; table with errors
(dc/defcard
  (v/table #{1 2 3}))

;; ## JS Objects
(dc/defcard
  (js/document.querySelectorAll ".mt-2"))

(dc/defcard
  js/window)

(dc/defcard
  (j/obj :foo "bar" :baz identity))

(dc/defcard
  (js/Array. 1 2 3))

(dc/defcard
  (j/lit {:a {:b 1 :c 2} :d 3}))

;; pagination for objects
(dc/defcard
  (into-array (range 30)))

(dc/defcard
  (reduce #(j/assoc! %1 (str "key" %2) %2)
          (j/obj)
          (range 30)))

(dc/defcard
  (j/obj :a (into-array (range 21))))

(dc/defcard
  (js/Array. 1 (into-array (range 2 23))))

;; mixed array/objects
(dc/defcard
  (clj->js [1 (j/obj :a 2) 3]))

(dc/defcard
  (clj->js {:a [1 {:b 2} 3]}))

;; **TODO**: fix missing cljs.core/array in SCI ctx
(dc/defcard
  (j/lit [1 2 3]))

;; this one won't work when advanced-compiled
(dc/defcard
  (let [a (j/get-in js/window (map munge '[cljs core array]))]
    (a 1 2 3)))

;; ## Code
(dc/defcard
  (v/code "(defn the-answer
  \"to all questions\"
  []
  (inc #_ #readme/as :ignore 41)"))

;; ## Vars
(dc/defcard
  (var v/doc-url))

;; ## Reagent
(dc/defcard
 (reagent/as-element [:h1 "♻️"]))

(dc/defcard
  (v/with-viewer :reagent
                 (fn []
                   (reagent/with-let [c (reagent/atom 0)]
                                     [:<>
                                      [:h2 "Count: " @c]
                                      [:button.rounded.bg-blue-500.text-white.py-2.px-4.font-bold.mr-2 {:on-click #(swap! c inc)} "increment"]
                                      [:button.rounded.bg-blue-500.text-white.py-2.px-4.font-bold {:on-click #(swap! c dec)} "decrement"]]))))

;; ## Using `v/with-viewer`
(dc/defcard
  (v/with-viewer
   #(v/html
     [:div.relative
      [:div.h-2.mb-4.flex.rounded.bg-blue-200.overflow-hidden
       [:div.shadow-none.flex.flex-col.text-center.bg-blue-500
        {:style {:width (-> %
                            (* 100)
                            int
                            (max 0)
                            (min 100)
                            (str "%"))}}]]])
   0.33))

;; ## Notebook Viewer
(dc/defcard
  (v/with-viewer :clerk/notebook
                 {:blocks (map v/present
                               [(v/with-viewer :markdown "# Hello Markdown\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum velit nulla, sodales eu lorem ut, tincidunt consectetur diam. Donec in scelerisque risus. Suspendisse potenti. Nunc non hendrerit odio, at malesuada erat. Aenean rutrum quam sed velit mollis imperdiet. Sed lacinia quam eget tempor tempus. Mauris et leo ac odio condimentum facilisis eu sed nibh. Morbi sed est sit amet risus blandit ullam corper. Pellentesque nisi metus, feugiat sed velit ut, dignissim finibus urna.")
                                (v/code "(shuffle (range 10))")
                                (v/with-viewer :clerk/code-block {:text "(+ 1 2 3)"})
                                (v/md "# And some more\n And some more [markdown](https://daringfireball.net/projects/markdown/).")
                                (v/code "(shuffle (range 10))")
                                (v/md "## Some math \n This is a formula.")
                                (v/tex "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")
                                (v/plotly {:data [{:y (shuffle (range 10)) :name "The Federation"}
                                                  {:y (shuffle (range 10)) :name "The Empire"}]})])}))

;; ## Layouts
;; **FIXME**:  `v/html` cannot be nested
(dc/defcard
  (v/col
   (v/row (v/html [:h1 "🎲"]) (v/html [:h1 "🎲"]))
   (v/row (v/html [:h1 "🎲"]) (v/html [:h1 "🎲"]))))

;; unlike on the JVM side
(clerk/col
 (clerk/row (clerk/html [:h1 "🎲"]) (clerk/html [:h1 "🎲"]))
 (clerk/row (clerk/html [:h1 "🎲"]) (clerk/html [:h1 "🎲"])))

;; in order for it to work, one needs the verbose syntax
(dc/defcard
  (v/col
   (v/row (v/with-viewer :html [:h1 "🎲"]) (v/with-viewer :html [:h1 "🎲"]))
   (v/row (v/with-viewer :html [:h1 "🎲"]) (v/with-viewer :html [:h1 "🎲"]))))

;; ## In-process Pagination

(dc/defcard
  (range))

(dc/defcard
  (range 21))

(dc/defcard
  {:a (range 21)})
