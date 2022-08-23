;; # 🃏 CLJS Cards
^{:nextjournal.clerk/toc true}
(ns ^:nextjournal.clerk/no-cache cards
  (:require [nextjournal.clerk :as clerk]))

^{::clerk/visibility {:code :hide :result :hide}}
(def card-viewer
  {:transform-fn (comp clerk/mark-presented (v/update-val v/->viewer-eval))
   :render-fn '(fn [data]
                 (let [is-valid-element? ;; private
                       (j/get-in js/window
                                 (map munge '[nextjournal clerk sci-viewer valid-react-element?]))]
                   (if (is-valid-element? data)
                     data
                     (v/html [v/inspect data]))))})

^{::clerk/visibility {:code :hide :result :hide}}
(defmacro card [body] `(clerk/with-viewer card-viewer '~body))

;; ## Latex
(card
  (v/tex "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}"))

;; ## Tables
(card
  (v/table {:a [1 2 3] :b [4 5 6]}))

;; incomplete tables
(card
  (v/table {:a [1 2 3] :b [4]}))

;; table with errors
(card
  (v/table #{1 2 3}))


;; ## JS Objects
(card
  (j/obj :foo "bar"))

(card
  (js/document.querySelectorAll ".mt-2"))

;; ## Vars
(card
  (var v/doc-url))

;; ## Reagent
(card
  (v/with-viewer :reagent
                 (fn []
                   (reagent/with-let [c (reagent/atom 0)]
                                     [:<>
                                      [:h2 "Count: " @c]
                                      [:button.rounded.bg-blue-500.text-white.py-2.px-4.font-bold.mr-2 {:on-click #(swap! c inc)} "increment"]
                                      [:button.rounded.bg-blue-500.text-white.py-2.px-4.font-bold {:on-click #(swap! c dec)} "decrement"]]))))


;; ## Using `v/with-viewer`
(card
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
(card
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
