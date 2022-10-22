;; # 📓 Book of Clerk
^{:nextjournal.clerk/visibility {:code :hide}}
(ns nextjournal.clerk.book
  {:nextjournal.clerk/toc true}
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.analyzer :as ana]
            [nextjournal.clerk.viewer :as v]
            [sicmutils.env :as sicm]
            [weavejester.dependency :as dep])
  (:import (javax.imageio ImageIO)
           (java.net URL)))

;; ## ⚖️ Rationale

;; Computational notebooks allow arguing from evidence by mixing prose with executable code. For a good overview of problems users encounter in traditional notebooks like Jupyter, see [I don't like notebooks](https://www.youtube.com/watch?v=7jiPeIFXb6U) and [What’s Wrong with Computational Notebooks? Pain Points, Needs, and Design Opportunities](https://web.eecs.utk.edu/\~azh/pubs/Chattopadhyay2020CHI_NotebookPainpoints.pdf).

;; Specifically Clerk wants to address the following problems:

;; * Less helpful than my editor
;; * Notebook code being hard to reuse
;; * Reproduction problems coming from out-of-order execution
;; * Problems with archival and putting notebooks in source control

;; Clerk is a notebook library for Clojure that aims to address these problems by doing less, namely:

;; * no editing environment, folks can keep using the editors they know and love
;; * no new format: Clerk notebooks are regular Clojure namespaces (interspersed with markdown comments). This also means Clerk notebooks are meant to be stored in source control.
;; * no out-of-order execution: Clerk notebooks always evaluate from top to bottom. Clerk builds a dependency graph of Clojure vars and only recomputes the needed changes to keep the feedback loop fast.
;; * no external process: Clerk runs inside your Clojure process, giving Clerk access to all code on the classpath.

;; ## 🚀 Getting Started

;; ### 🤹 Clerk Demo

;; When you're not yet familiar with Clerk, we recommend cloning and playing with the [nextjournal/clerk-demo](https://github.com/nextjournal/clerk-demo) repo.
;; ```sh
;; git clone git@github.com:nextjournal/clerk-demo.git
;; cd clerk-demo
;; ```

;; Then open `dev/user.clj` from the project in your favorite editor start a REPL into the project, see
;; * [Emacs & Cider](https://docs.cider.mx/cider/basics/up_and_running.html#launch-an-nrepl-server-from-emacs)
;; * [Calva](https://calva.io/jack-in-guide/)
;; * [Cursive](https://cursive-ide.com/userguide/repl.html)
;; * [Vim & Neovim](https://github.com/clojure-vim/vim-jack-in)

;; ### 🔌 In an Existing Project

;; To use Clerk in your project, add the following dependency to your `deps.edn`:

;; ```edn
;; {:deps {io.github.nextjournal/clerk {:mvn/version "0.9.513"}}}
;; ```

;; Require and start Clerk as part of your system start, e.g. in `user.clj`:

;; ```clojure
;; (require '[nextjournal.clerk :as clerk])

;; ;; start Clerk's built-in webserver on the default port 7777, opening the browser when done
;; (clerk/serve! {:browse? true})

;; ;; either call `clerk/show!` explicitly to show a given notebook.
;; (clerk/show! "notebooks/rule_30.clj")
;; ```

;; You can then access Clerk at <http://localhost:7777>.

;; ### ⏱ File Watcher

;; You can load, evaluate, and present a file with the clerk/show! function, but in most cases it's easier to start a file watcher with something like:

;; ```clojure
;; (clerk/serve! {:watch-paths ["notebooks" "src"]})
;; ```
;; ... which will automatically reload and re-eval any clj or md files that change, displaying the most recently changed one in your browser.

;; To make this performant enough to feel good, Clerk caches the computations it performs while evaluating each file. Likewise, to make sure it doesn't send too much data to the browser at once, Clerk paginates data structures within an interactive viewer.


;; ### 🔪 Editor Integration

;; A recommended alternative to the file watcher is setting up a hotkey in your editor to save & `clerk/show!` the active file.

;; **Emacs**

;; In Emacs, add the following to your config:

;; ```elisp
;; (defun clerk-show ()
;;   (interactive)
;;   (when-let
;;       ((filename
;;         (buffer-file-name)))
;;     (save-buffer)
;;     (cider-interactive-eval
;;      (concat "(nextjournal.clerk/show! \"" filename "\")"))))
;;
;; (define-key clojure-mode-map (kbd "<M-return>") 'clerk-show)
;; ```

;; **IntelliJ/Cursive**

;; In IntelliJ/Cursive, you can [set up REPL commands](https://cursive-ide.com/userguide/repl.html#repl-commands) via:

;; * going to `Tools→REPL→Add New REPL Command`, then
;; * add the following command: `(show! "~file-path")`;
;; * make sure the command is executed in the `nextjournal.clerk` namespace;
;; * lastly assign a shortcut of your choice via `Settings→Keymap`

;; **Neovim + Conjure**

;; With [neovim](https://neovim.io/) + [conjure](https://github.com/Olical/conjure/) one can use the following vimscript function to save the file and show it with Clerk:

;; ```
;; function! ClerkShow()
;; exe "w"
;; exe "ConjureEval (nextjournal.clerk/show! \"" . expand("%:p") . "\")"
;; endfunction

;; nmap <silent> <localleader>cs :execute ClerkShow()<CR>
;; ```



;; ## 🔍 Viewers

;; Clerk comes with a number of useful built-in viewers e.g. for
;; Clojure data, html & hiccup, tables, plots &c.

;; When showing large data structures, Clerk's default viewers will
;; paginate the results.

;; ### 🧩 Clojure Data
;; The default set of viewers are able to render Clojure data.
(def clojure-data
  {:hello "world 👋"
   :tacos (map #(repeat % '🌮) (range 1 30))
   :zeta "The\npurpose\nof\nvisualization\nis\ninsight,\nnot\npictures."})

;; Viewers can handle lazy infinite sequences, partially loading data
;; by default with the ability to load more data on request.
(range)

(def fib (lazy-cat [0 1] (map + fib (rest fib))))

;; In addition, there's a number of built-in viewers that can be
;; called explicity using functions.


;; ### 🌐 Hiccup, HTML & SVG

;; The `html` viewer interprets `hiccup` when passed a vector.
(clerk/html [:div "As Clojurians we " [:em "really"] " enjoy hiccup"])

;; Alternatively you can pass it an HTML string.
(clerk/html "Never <strong>forget</strong>.")

;; You can style elements, using [Tailwind CSS](https://tailwindcss.com/docs/utility-first).
(clerk/html [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1 "✨ Tailwind CSS"])

;; The `html` viewer is also able to display SVG, taking either a hiccup vector or a SVG string.
(clerk/html [:svg {:width 500 :height 100}
             [:circle {:cx  25 :cy 50 :r 25 :fill "blue"}]
             [:circle {:cx 100 :cy 75 :r 25 :fill "red"}]])

;; ### 🔢 Tables

;; Clerk provides a built-in data table viewer that supports the three
;; most common tabular data shapes out of the box: a sequence of maps,
;; where each map's keys are column names; a seq of seq, which is just
;; a grid of values with an optional header; a map of seqs, in with
;; keys are column names and rows are the values for that column.

(clerk/table [[1 2]
              [3 4]]) ;; seq of seqs

(clerk/table (clerk/use-headers [["odd numbers" "even numbers"]
                                 [1 2]
                                 [3 4]])) ;; seq of seqs with header

(clerk/table [{"odd numbers" 1 "even numbers" 2}
              {"odd numbers" 3 "even numbers" 4}]) ;; seq of maps

(clerk/table {"odd numbers" [1 3]
              "even numbers" [2 4]}) ;; map of seqs


;; Internally the table viewer will normalize all of the above to a
;; map with `:rows` and an optional `:head` key, also giving you
;; control over the column order.
(clerk/table {:head ["odd numbers" "even numbers"]
              :rows [[1 2] [3 4]]}) ;; map with `:rows` and optional `:head` keys



;; ### 🧮 TeX

;; As we've already seen, all comment blocks can contain TeX (we use
;; [KaTeX](https://katex.org/) under the covers). In addition, you can
;; call the TeX viewer programmatically. Here, for example, are
;; Maxwell's equations in differential form:
(clerk/tex "
\\begin{alignedat}{2}
  \\nabla\\cdot\\vec{E} = \\frac{\\rho}{\\varepsilon_0} & \\qquad \\text{Gauss' Law} \\\\
  \\nabla\\cdot\\vec{B} = 0 & \\qquad \\text{Gauss' Law ($\\vec{B}$ Fields)} \\\\
  \\nabla\\times\\vec{E} = -\\frac{\\partial \\vec{B}}{\\partial t} & \\qquad \\text{Faraday's Law} \\\\
  \\nabla\\times\\vec{B} = \\mu_0\\vec{J}+\\mu_0\\varepsilon_0\\frac{\\partial\\vec{E}}{\\partial t} & \\qquad \\text{Ampere's Law}
\\end{alignedat}
")

;; ### 📊 Plotly

;; Clerk also has built-in support for Plotly's low-ceremony plotting.
;; See [Plotly's JavaScript docs](https://plotly.com/javascript/) for more examples and [options](https://plotly.com/javascript/configuration-options/).
(clerk/plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]
               :layout {:margin {:l 20 :r 0 :b 20 :t 20}}
               :config {:displayModeBar false
                        :displayLogo false}})

;; ### 🗺 Vega Lite

;; But Clerk also has Vega Lite for those who prefer that grammar.
(clerk/vl {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                         :format {:type "topojson" :feature "counties"}}
           :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                            :key "id" :fields ["rate"]}}]
           :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}
           :embed/opts {:actions false}})

;; You can provide a map of [embed options](https://github.com/vega/vega-embed#embed) to the vega viewer via the `:embed/opts` key.

;; ### 🎼 Code

;; The code viewer uses
;; [clojure-mode](https://nextjournal.github.io/clojure-mode/) for
;; syntax highlighting.
(clerk/code (macroexpand '(when test
                            expression-1
                            expression-2)))

(clerk/code '(ns foo "A great ns" (:require [clojure.string :as str])))

(clerk/code "(defn my-fn\n  \"This is a Doc String\"\n  [args]\n  42)")

;; ### 🏞 Images

;; Clerk now has built-in support for the
;; `java.awt.image.BufferedImage` class, which is the native image
;; format of the JVM.
;;
;; When combined with `javax.imageio.ImageIO/read`, one can easily
;; load images in a variety of formats from a `java.io.File`, an
;; `java.io.InputStream`, or any resource that a `java.net.URL` can
;; address.
;;
;; For example, we can fetch a photo of De zaaier, Vincent van Gogh's
;; famous painting of a farmer sowing a field from Wiki Commons like
;; this:

(ImageIO/read (URL. "https://upload.wikimedia.org/wikipedia/commons/thumb/3/31/The_Sower.jpg/1510px-The_Sower.jpg"))

;; We've put some effort into making the default image rendering
;; pleasing. The viewer uses the dimensions and aspect ratio of each
;; image to guess the best way to display it in classic DWIM
;; fashion. For example, an image larger than 900px wide with an
;; aspect ratio larger then two will be displayed full width:

(ImageIO/read (URL. "https://images.unsplash.com/photo-1532879311112-62b7188d28ce?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8"))

;; On the other hand, smaller images are centered and shown using their intrinsic dimensions:

(ImageIO/read (URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"))

;; ### 📒 Markdown

;; The same Markdown support Clerk uses for comment blocks is also
;; available programmatically:
(clerk/md (clojure.string/join "\n" (map #(str "* Item " (inc %)) (range 3))))

;; ### 🔠 Grid Layouts

;; Layouts can be composed via `row`s and `col`s
;;
;; Passing `:width`, `:height` or any other style attributes to
;; `::clerk/opts` will assign them on the row or col that contains
;; your items. You can use this to size your containers accordingly.

^{::clerk/visibility {:code :hide :result :hide}}
(def image-1 (ImageIO/read (URL. "https://etc.usf.edu/clipart/62300/62370/62370_letter-a_lg.gif")))

^{::clerk/visibility {:code :hide :result :hide}}
(def image-2 (ImageIO/read (URL. "https://etc.usf.edu/clipart/72700/72783/72783_floral_b_lg.gif")))

^{::clerk/visibility {:code :hide :result :hide}}
(def image-3 (ImageIO/read (URL. "https://etc.usf.edu/clipart/72700/72787/72787_floral_c_lg.gif")))


(clerk/row image-1 image-2 image-3)

(clerk/col {::clerk/opts {:width 150}} image-1 image-2 image-3)

;; Laying out stuff is not limited to images. You can use it to lay
;; out any Clerk viewer. E.g. combine it with HTML viewers to render
;; nice captions:

(defn caption [text]
  (clerk/html [:span.text-slate-500.text-xs.text-center.font-sans text]))

(clerk/row
 (clerk/col image-1 (caption "Figure 1: Decorative A"))
 (clerk/col image-2 (caption "Figure 2: Decorative B"))
 (clerk/col image-3 (caption "Figure 3: Decorative C")))

;; **Alternative notations**
;;
;; By default, `row` and `col` operate on `& rest` so you can pass any
;; number of items to the functions.  But the viewers are smart enough
;; to accept any sequential list of items.

(v/row [image-1 image-2 image-3])

;; ### 🍱 Composing Viewers

;; Viewers compose, so you can for example use the plotly viewer inside the grid viewers.

^{::clerk/visibility {:code :fold}}
(do
  (def donut-chart
    (clerk/plotly {:data [{:values [27 11 25 8 1 3 25]
                           :labels ["US" "China" "European Union" "Russian Federation" "Brazil" "India" "Rest of World"]
                           :text "CO2"
                           :textposition "inside"
                           :domain {:column 1}
                           :hoverinfo "label+percent+name"
                           :hole 0.4
                           :type "pie"}]
                   :layout {:showlegend false
                            :width 200
                            :height 200
                            :annotations [{:font {:size 20} :showarrow false :x 0.5 :y 0.5 :text "CO2"}]}
                   :config {:responsive true}}))

  (def contour-plot
    (clerk/plotly {:data [{:z [[10 10.625 12.5 15.625 20]
                               [5.625 6.25 8.125 11.25 15.625]
                               [2.5 3.125 5.0 8.125 12.5]
                               [0.625 1.25 3.125 6.25 10.625]
                               [0 0.625 2.5 5.625 10]]
                           :type "contour"}]})))

(clerk/col (clerk/row donut-chart donut-chart donut-chart)
           contour-plot)

;; ### 🤹🏻 Applying Viewers

;; **Metadata Notation**

;; In the examples above, we've used convience helper functions like
;; `clerk/html` or `clerk/plotly` to wrap values in a viewer. If you
;; call this on the REPL, you'll notice a given value gets wrapped in
;; a map under the `:nextjournal/value` key with the viewer being in
;; the `:nextjournal/viewer` key.

;; You can also select a viewer using Clojure metadata in order to
;; avoid Clerk interfering with the value.

^{::clerk/viewer clerk/table}
(def my-dataset
  [{:temperature 41.0 :date (java.time.LocalDate/parse "2022-08-01")}
   {:temperature 39.0 :date (java.time.LocalDate/parse "2022-08-01")}
   {:temperature 34.0 :date (java.time.LocalDate/parse "2022-08-01")}
   {:temperature 29.0 :date (java.time.LocalDate/parse "2022-08-01")}])

;; ### 👁 Writing Viewers

;; Let's explore how Clerk viewers work and how you create your own to
;; gain better insight into your problem at hand.

v/default-viewers

;; These are the default viewers that come with Clerk.

(into #{} (map type) v/default-viewers)

;; Each viewer is a simple Clojure map.


(assoc (frequencies (mapcat keys v/default-viewers)) :total (count v/default-viewers))

;; We have a total of 41 viewers in the defaults. Let's start with a
;; simple example and explain the different extensions points in the
;; viewer api.


;; #### 🎪 Presentation

;; Clerk's rendering happens in the browser. On the Clojure-side, a
;; given document is _presented_. Presenting takes a value and
;; transforms it such that Clerk can send it to the browser where it
;; will be rendered.


^{::clerk/visibility {:code :fold :result :hide}}
(do
  (set! *print-namespace-maps* false)
  (defn show-raw-value [x]
    (clerk/code (with-out-str (clojure.pprint/pprint x)))))

;; Let's start with one of the simplest examples. You can see that
;; `present` takes our value `1` and transforms it into a map, with
;; `1` under a `:nextjournal/value` key and the number viewer assigned
;; under the `:nextjournal/viewer` key.  We call this map a
;; `wrapped-value`.


^{::clerk/viewer show-raw-value}
(v/present 1)

;; This data structure is is sent over Clerk's websocket to the
;; browser, where it will be displayed using the `:render-fn` found in
;; the `:nextjournal/viewer` key.

;; Now onto something slightly more complex, `#{1 2 3}`.

^{::clerk/viewer show-raw-value}
(v/present #{1 2 3})


;; Here, we're giving it a set with 1, 2, 3 in it. In its generalized
;; form, `present` is a function that does a depth-first traversal of
;; a given tree, starting at the root node. It will select a viewer
;; for this root node, and unless told otherwise, descend further down
;; the tree to present its child nodes.
;;
;; Compare this with the simple `1` example above! You should
;; recognize the leaf values. Also note that the container is no
;; longer a set, but it has been transformed into a vector. This
;; transformation exists to support pagination of long unordered
;; sequences like maps and sets and so we can efficiently access a
;; value inside this tree using `get-in`.

;; You might ask yourself why we don't just send the unmodified value
;; to the browser. For one, we could easily overload the browser with
;; too much data. Secondly we will look at examples of being able to
;; select viewers based on Clojure and Java types, which cannot be
;; serialized and sent to the browser.


;; #### ⚙️ Transform

;; When writing your own viewer, the first extention point you should reach for is `:tranform-fn`. 

#_ "exercise: wrap this in `v/present` and call it at the REPL"
(v/with-viewer {:transform-fn #(clerk/html [:pre (pr-str %)])}
  "Exploring the viewer api")

;; As you can see the argument to the `:transform-fn` isn't just the
;; string we're passing it, but a `wrapped-value`. We will look at
;; what this enables in a bit. But let's look at one of the simplest
;; examples first.

;; **A first simple example**

(def greet-viewer
  {:transform-fn (clerk/update-val #(clerk/html [:strong "Hello, " % " 👋"]))})

;; For this simple `greet-viewer` we're only doing a simple value transformation. For this, `clerk/update-val` is a small helper function which takes a function `f` and returns a function to update only the value inside a `wrapped-value`, a shorthand for `#(update % :nextjournal/val f)`

(v/with-viewer greet-viewer
  "James Clerk Maxwell")

;; The `:transform-fn` runs on the JVM, which means you can explore what it does at your REPL by calling `v/present` on such a value.
^{::clerk/viewer show-raw-value}
(v/present (v/with-viewer greet-viewer
             "James Clerk Maxwell"))


;; **Passing modified viewers down the tree** 

#_ "TODO: move this into clerk?"
(defn add-child-viewers [viewer viewers]
  (update viewer :transform-fn (fn [transform-fn-orig]
                                 (fn [wrapped-value]
                                   (update (transform-fn-orig wrapped-value) :nextjournal/viewers clerk/add-viewers viewers)))))

v/table-viewer

(def custom-table-viewer
  (add-child-viewers v/table-viewer
                     [(assoc v/table-head-viewer :transform-fn (v/update-val (partial map (comp (partial str "Column: ") str/capitalize name))))
                      (assoc v/table-missing-viewer :render-fn '(fn [x] [:span.red "N/A"]))]))

(clerk/with-viewer custom-table-viewer
  {:col/a [1 2 3 4] :col/b [1 2 3] :col/c [1 2 3]})

(clerk/with-viewer custom-table-viewer
  {:col/a [1 2 3 4] :col/b [1 2 3] :col/c [1 2 3]})

;; #### 🐢 Recursion

;; But this presentation and hence tranformation of nodes
;; further down the tree isn't always what you want. For example, the
;; `plotly` or `vl` viewers want to receive the child value unaltered
;; in order to use it as a spec.
;;
;; To stop Clerk's presentation from descending into child nodes, use
;; `clerk/mark-presented` as a `:transform-fn`. Compare the result
;; below in which `[1 2 3]` appears unaltered with what you see above.

^{::clerk/viewer show-raw-value}
(v/present (clerk/with-viewer {:transform-fn clerk/mark-presented
                               :render-fn '(fn [x] [:pre (pr-str x)])}
             [1 2 3]))

;; Clerk's presentation will also transform maps into sequences in
;; order to paginate large maps. When you're dealing with a map that
;; you know is bounded and would like to preserve its keys, there's
;; `clerk/mark-preserve-keys`. This will still transform (and
;; paginate) the values of the map, but leave the keys unaltered.

^{::clerk/viewer show-raw-value}
(v/present (clerk/with-viewer {:transform-fn clerk/mark-preserve-keys}
             {:hello 42}))


;; #### 🔬 Render

;; As we've just seen, you can also do a lot with `:transform-fn` and
;; using `clerk/html` on the JVM. When you want to run code in the
;; browser where Clerk's viewers are rendered, reach for
;; `:render-fn`. As an example, we'll write a multiviewer for a
;; sicmutils literal expression that will compute two alternative
;; representations and let the user switch between them in the
;; browser.

;; We start with a simple function that takes a such an expression and
;; turns it into a map with two representation, one TeX and the
;; original form.

(defn transform-literal [expr]
  {:TeX (-> expr sicm/->TeX clerk/tex)
   :original (clerk/code (with-out-str (sicm/print-expression (sicm/freeze expr))))})

;; Our `literal-viewer` calls this `transform-literal` function and
;; also calls `clerk/mark-preserve-keys`. This tells Clerk to leave
;; the keys of the map as-is.

;; In our `:render-fn`, which is called in the browser we will recieve
;; this map. Note that this is a quoted form, not a function. Clerk
;; will send this form to the browser for evaluation. There it will
;; create a `reagent/atom` that holds the selection state. Lastly,
;; `v/inspect-presented` is a component that takes a `wrapped-value`
;; that ran through `v/present` and show it.

(def literal-viewer
  {:pred sicmutils.expression/literal?
   :transform-fn (comp clerk/mark-preserve-keys
                       (clerk/update-val transform-literal))
   :render-fn '(fn [label->val]
                 (reagent/with-let [!selected-label (reagent/atom (ffirst label->val))]
                   [:<> (into
                         [:div.flex.items-center.font-sans.text-xs.mb-3
                          [:span.text-slate-500.mr-2 "View-as:"]]
                         (map (fn [label]
                                [:button.px-3.py-1.font-medium.hover:bg-indigo-50.rounded-full.hover:text-indigo-600.transition
                                 {:class (if (= @!selected-label label) "bg-indigo-100 text-indigo-600" "text-slate-500")
                                  :on-click #(reset! !selected-label label)}
                                 label]))
                         (keys label->val))
                    [v/inspect-presented (get label->val @!selected-label)]]))})

;; Now let's see if this works. Try switching to the original
;; representation!

^{::clerk/viewer literal-viewer}
(sicm/+ (sicm/square (sicm/sin 'x))
        (sicm/square (sicm/cos 'x)))

;; #### 🥇 Selection

;; Without a viewer specified, Clerk will go through the sequence of
;; viewers and apply the `:pred` function in the viewer to find a
;; matching one. Use `v/viewer-for` to select a viewer for a given
;; value.
(def char?-viewer
  (v/viewer-for v/default-viewers \A))

;; If we select a specific viewer (here the `v/html-viewer` using
;; `clerk/html`) this is the viewer we will get.
(def html-viewer
  (v/viewer-for v/default-viewers (clerk/html [:h1 "foo"])))

;; Instead of specifying a viewer for every value, we can also modify
;; the viewers per namespace. Here, we add the `literal-viewer` from
;; above to the whole namespace.

(clerk/add-viewers! [literal-viewer])

;; As you can see we now get this viewer automatically, without
;; needing to explicitly select it.
(sicm/+ (sicm/square (sicm/sin 'x))
        (sicm/square (sicm/cos 'x)))

;; #### 🔓 Elisions

(def string?-viewer
  (v/viewer-for v/default-viewers "Denn wir sind wie Baumstämme im Schnee."))

;; Notice that for the `string?` viewer above, there's a `:page-size`
;; of `80`. This is the case for all collection viewers in Clerk and
;; controls how many elements are displayed. So using the default
;; `string?-viewer` above, we're showing the first 80 characters.
(def long-string
  (str/join (into [] cat (repeat 10 "Denn wir sind wie Baumstämme im Schnee.\n"))))

;; If we change the viewer and set a different `:n` in `:page-size`, we only see 10 characters.
(v/with-viewer (assoc string?-viewer :page-size 10)
  long-string)

;; Or, we can turn off eliding, by dissoc'ing `:page-size` alltogether.
(v/with-viewer (dissoc string?-viewer :page-size)
  long-string)

;; The operations above were changes to a single viewer. But we also
;; have a function `update-viewers` to update a given viewers by
;; applying a `select-fn->update-fn` map. Here, the predicate is the
;; keyword `:page-size` and our update function is called for every
;; viewer with `:page-size` and is dissoc'ing them.
(def without-pagination
  {:page-size #(dissoc % :page-size)})

;; Here's the updated-viewers:
(def viewers-without-lazy-loading
  (v/update-viewers v/default-viewers without-pagination))

;; Now let's confirm these modified viewers don't have `:page-size`
;; on them anymore.
(filter :page-size viewers-without-lazy-loading)

;; And compare it with the defaults:
(filter :page-size v/default-viewers)

;; Now let's display our `clojure-data` var from above using these
;; modified viewers.
(clerk/with-viewers viewers-without-lazy-loading
  clojure-data)

;; #### 👷 Loading Libraries

;; This is a custom viewer for
;; [Mermaid](https://mermaid-js.github.io/mermaid), a markdown-like
;; syntax for creating diagrams from text. Note that this library
;; isn't bundles with Clerk but we use a component based on
;; [d3-require](https://github.com/d3/d3-require) to load it at
;; runtime.


(def mermaid-viewer
  {:transform-fn clerk/mark-presented
   :render-fn '(fn [value]
                 (v/html
                  (when value
                    [v/with-d3-require {:package ["mermaid@8.14/dist/mermaid.js"]}
                     (fn [mermaid]
                       [:div {:ref (fn [el] (when el
                                              (.render mermaid (str (gensym)) value #(set! (.-innerHTML el) %))))}])])))})

;; We can then use  the above viewer using `with-viewer`.
(clerk/with-viewer mermaid-viewer
  "stateDiagram-v2
    [*] --> Still
    Still --> [*]

    Still --> Moving
    Moving --> Still
    Moving --> Crash
    Crash --> [*]")

;; ## 🙈 Controlling Visibility

;; Visibility for code and results can be controlled document-wide and
;; per top-level form. By default, Clerk will always show code and 
;; results for a notebook.

;; You can use a map of the following shape to set the visibility of
;; code and results individually:
;;
;;    {:nextjournal.clerk/visibility {:code :hide :result :show}}
;;
;; The above example will hide the code and show only results.
;;
;; Valid values are `:show`, `:hide` and `:fold` (only available for `:code`).
;; Using `{:code :fold}` will hide the code cell initially but show an
;; indicator to toggle its visibility:

^{::clerk/visibility {:code :fold}} (shuffle (range 25))

;; The visibility map can be used in the following ways:

;; **Set document defaults via the `ns` form**
;;
;;    (ns visibility
;;      {:nextjournal.clerk/visibility {:code :fold}})
;;
;; The above example will hide all code cells by default but show an indicator
;; to toggle their visibility instead. In this case results will always show
;; because that’s the default.
;;
;; **As metadata to control a single top-level form**
;;
;;    ^{::clerk/visibility {:code :hide}} (shuffle (range 25))
;;
;; This will hide the code but only show the result: 

^{::clerk/visibility {:code :hide}} (shuffle (range 25))

;; Setting visibility as metadata will override the document-wide
;; visibility settings for this one specific form.

;; **As top-level form to change the document defaults**
;;
;; Independently of what defaults are set via your `ns` form,
;; you can use a top-level map as a marker to override the visibility
;; settings for any forms following it.
;;
;; Example: Code is hidden by default but you want to show code for all
;; top-level forms after a certain point:
;;
;;    (ns visibility
;;      {:nextjournal.clerk/visibility {:code :hide}})
;;
;;    (+ 39 3) ;; code will be hidden
;;    (range 25) ;; code will be hidden
;;    
;;    {:nextjournal.clerk/visibility {:code :show}}
;;    
;;    (range 500) ;; code will be visible
;;    (rand-int 42) ;; code will be visible
;;
;; This comes in quite handy for debugging too!

;; ## ⚡️ Incremental Computation

;; ### 🔖 Parsing

;; First, we parse a given Clojure file using `rewrite-clj`.
(def parsed
  (parser/parse-file "book.clj"))

;; ### 🧐 Analysis

;; Then, each expression is analysed using
;; `tools.analyzer`. A dependency graph, the analyzed form and the
;; originating file is recorded.

(def analyzed
  (ana/build-graph parsed))


;; This analysis is done recursively, descending into all dependency symbols.

(ana/find-location 'nextjournal.clerk.analyzer/analyze-file)

(ana/find-location `dep/depend)

(ana/find-location 'io.methvin.watcher.DirectoryChangeEvent)

(ana/find-location 'java.util.UUID)

(let [{:keys [graph]} analyzed]
  (dep/transitive-dependencies graph 'nextjournal.clerk.book/analyzed))

;; ### 🪣 Hashing
;; Then we can use this information to hash each expression.
(def hashes
  (:->hash (ana/hash analyzed)))

;; ### 🗃 Cached Evaluation

;; Clerk uses the hashes as filenames and only
;; re-evaluates forms that haven't been seen before. The cache is
;; using [nippy](https://github.com/ptaoussanis/nippy).
(def rand-fifteen
  (do (Thread/sleep 10)
      (shuffle (range 15))))

;; We can look up the cache key using the var name in the hashes map.
(when-let [form-hash (get hashes 'nextjournal.clerk.book/rand-fifteen)]
  (let [hash (slurp (eval/->cache-file (str "@" form-hash)))]
    (eval/thaw-from-cas hash)))

;; As an escape hatch, you can tag a form or var with
;; `::clerk/no-cache` to always re-evaluate it. The following form
;; will never be cached.
^::clerk/no-cache (shuffle (range 42))

;; For side effectful functions that should be cached, like a database
;; query, you can add a value like this `#inst` to control when
;; evaluation should happen.

(def query-results
  (let [_run-at #_(java.util.Date.) #inst "2021-05-20T08:28:29.445-00:00"
        ds (next.jdbc/get-datasource {:dbtype "sqlite" :dbname "chinook.db"})]
    (with-open [conn (next.jdbc/get-connection ds)]
      (clerk/table (next.jdbc/execute! conn ["SELECT AlbumId, Bytes, Name, TrackID, UnitPrice FROM tracks"])))))
