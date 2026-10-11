;; # 🧩 Datastar Client Components
;; With `:frontend :datastar` these viewers render on the client via
;; custom elements, everything else renders on the server.
(ns datastar-components
  (:require [nextjournal.clerk :as clerk]))

;; ## Formulas
;; Inline $E = mc^2$ and block formulas are rendered with KaTeX:
;;
;; $$\int_0^\infty e^{-x^2} dx = \frac{\sqrt{\pi}}{2}$$

(clerk/tex "\\sum_{i=0}^n i = \\frac{n(n+1)}{2}")

;; ## Vega-Lite
(def n 20)

(clerk/vl {:width 500 :height 200
           :data {:values (map (fn [i] {:x i :y (* i i)}) (range n))}
           :mark {:type "line" :point true}
           :encoding {:x {:field "x" :type "quantitative"}
                      :y {:field "y" :type "quantitative"}}})

;; ## Plotly
(clerk/plotly {:data [{:z [[1 2 3] [3 2 1] [2 3 1]] :type "surface"}]
               :layout {:width 500 :height 400 :margin {:l 0 :r 0 :t 0 :b 0}}})

;; ## Syntax highlighting
;; Clojure code is highlighted on the server.
(defn ^:private greet
  "Greets `name`."
  [name]
  (str "Hello, " name \! " " :keyword #"regex" nil true 42))

;; Code blocks in markdown in other languages stay plain:
;; ```js
;; const x = 42
;; ```
