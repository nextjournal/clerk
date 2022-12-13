;; # 🃏 CLJS Cards
^{:nextjournal.clerk/toc true :nextjournal.clerk/visibility {:code :hide}}
(ns cards
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [cards-macro :as c]
            [nextjournal.clerk.viewer :as v]))

;; ## $\LaTeX$
(c/card
  (v/tex "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}"))

;; ## Vega Lite
(c/card
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
(c/card
  (v/table {:a [1 2 3] :b [4 5 6]}))

(c/card
  (v/table {:head ['A 'B 'C]
            :rows (map #(range 3) (range 2))}))

;; incomplete tables
(c/card
  (v/table {:a [1 2 3] :b [4]}))

;; table with errors
(c/card
  (v/table #{1 2 3}))

;; ## JS Objects
(c/card
  (js/document.querySelectorAll ".mt-2"))

(c/card
  js/window)

(c/card
  (j/obj :foo "bar" :baz identity))

(c/card
  (js/Array. 1 2 3))

(c/card
  (j/lit {:a {:b 1 :c 2} :d 3}))

;; pagination for objects
(c/card
  (into-array (range 30)))

(c/card
  (reduce #(j/assoc! %1 (str "key" %2) %2)
          (j/obj)
          (range 30)))

(c/card
  (j/obj :a (into-array (range 21))))

(c/card
  (js/Array. 1 (into-array (range 2 23))))

;; mixed array/objects
(c/card
  (clj->js [1 (j/obj :a 2) 3]))

(c/card
 (clj->js {:a [1 {:b 2} 3]}))

(c/card
 (j/lit [1 2 3]))

;; this one won't work when advanced-compiled
#_
(c/card
 (let [a (j/get-in js/window (map munge '[cljs core array]))]
   (a 1 2 3)))

;; ## Promises

;; Resolved
(c/card (.resolve js/Promise 42))

;; Resolving after 2s
(c/card (js/Promise. (fn [resolve reject]
                       (js/setTimeout #(resolve 42) 2000))))

;; Rejected (commented out because it logs an error to the console)
#_(c/card (.reject js/Promise (ex-info "boom 💥" {:additional :data})))

;; ## Code
(c/card
 (v/code "(defn the-answer
  \"to all questions\"
  []
  (inc #_ #readme/as :ignore 41)"))

;; ## Vars
(c/card
  (var v/doc-url))

;; ## Reagent
(c/card
 (reagent/as-element [:h1 "♻️"]))

(c/card
  (v/with-viewer :reagent
                 (fn []
                   (reagent/with-let [c (reagent/atom 0)]
                                     [:<>
                                      [:h2 "Count: " @c]
                                      [:button.rounded.bg-blue-500.text-white.py-2.px-4.font-bold.mr-2 {:on-click #(swap! c inc)} "increment"]
                                      [:button.rounded.bg-blue-500.text-white.py-2.px-4.font-bold {:on-click #(swap! c dec)} "decrement"]]))))

;; ## Using `v/with-viewer`
(c/card
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
^{::clerk/width :wide}
(c/card
 (v/with-viewer {:render-fn 'nextjournal.clerk.render/render-notebook
                 :transform-fn v/mark-presented}
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
(c/card
  (v/col
   (v/row (v/html [:h1 "🎲"]) (v/html [:h1 "🎲"]))
   (v/row (v/html [:h1 "🎲"]) (v/html [:h1 "🎲"]))))

;; unlike on the JVM side
(clerk/col
 (clerk/row (clerk/html [:h1 "🎲"]) (clerk/html [:h1 "🎲"]))
 (clerk/row (clerk/html [:h1 "🎲"]) (clerk/html [:h1 "🎲"])))

;; in order for it to work, one needs the verbose syntax
(c/card
  (v/col
   (v/row (v/with-viewer :html [:h1 "🎲"]) (v/with-viewer :html [:h1 "🎲"]))
   (v/row (v/with-viewer :html [:h1 "🎲"]) (v/with-viewer :html [:h1 "🎲"]))))

;; ## In-process Pagination

;; FIXME: pagination works here, but makes page unresponsive
#_
(c/card
  (range))

(c/card
  (range 21))

(c/card
 {:a (range 21)})

;; ## Parser API

^{::clerk/width :wide}
(c/card 
 (as-> ";; # 👋 Hello CLJS
;; This is `fold`
;;
;; $$(\\beta\\rightarrow\\alpha\\rightarrow\\beta)\\rightarrow\\beta\\rightarrow [\\alpha] \\rightarrow\\beta$$
;;
(defn fold [f i xs]
  (if (seq xs)
    (fold f (f i (first xs)) (rest xs))
    i))

(fold str \"\" (range 10))

;; ## And the usual Clerk's perks
(v/plotly {:data [{:y (shuffle (range 10)) :name \"The Federation\"}
                  {:y (shuffle (range 10)) :name \"The Empire\"}]})
;; tables
(v/table {:a [1 2 3] :b [4 5 6]})
;; html
(v/html [:h1 \"🧨\"])
"
     doc
   (nextjournal.clerk.parser/parse-clojure-string {:doc? true} doc)
   (update doc :blocks (partial map (fn [{:as b :keys [type text]}]
                                      (cond-> b
                                        (= :code type)
                                        (assoc :result
                                               {:nextjournal/value
                                                (let [val (eval (read-string text))]
                                                  ;; FIXME: this won't be necessary once we unify v/html in SCI env to be the same as in nextjournal.clerk.viewer
                                                  ;; v/html is currently html-render for supporting legacy render-fns
                                                  (cond->> val
                                                    (nextjournal.clerk.render/valid-react-element? val)
                                                    (v/with-viewer v/reagent-viewer)))})))))
   (v/with-viewer v/notebook-viewer {::clerk/width :wide} doc))
 )
