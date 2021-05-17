;; # Elements of Clerk
;; Like the idea of notebooks, but hate leaving your favorite editor? We present Clerk, a tool that enables a rich, local-first notebook experience using standard Clojure namespaces.
(ns elements
  (:require [clojure.string :as str]
            [nextjournal.clerk.lib :as obs.lib]
            [nextjournal.viewer :as v]))

(let [rule30 {[1 1 1] 0
              [1 1 0] 0
              [1 0 1] 0
              [1 0 0] 1
              [0 1 1] 1
              [0 1 0] 1
              [0 0 1] 1
              [0 0 0] 0}
      n 33
      g1 (assoc (vec (repeat n 0)) (/ (dec n) 2) 1)
      evolve #(mapv rule30 (partition 3 1 (repeat 0) (cons 0 %)))]
  (v/with-viewer
    (->> g1 (iterate evolve) (take 17))
    '(fn [board]
       (let [cell (fn [c] (vector :div.inline-block
                                  {:class (if (zero? c)
                                            "bg-white border-solid border-2 border-black"
                                            "bg-black")
                                   :style {:width 16 :height 16}}))
             row (fn [r] (into [:div.flex.inline-flex] (map cell) r))]
         (v/html (into [:div.flex.flex-col] (map row) board))))))

;; Clerk uses static analysis and a tiny bit of data flow to avoid needless recomputation.
(defn fix-case [s]
  (obs.lib/fix-case s))

(def long-thing
  (do
    (Thread/sleep 1000)
    (take 400 (map fix-case (str/split-lines (slurp "/usr/share/dict/words"))))))

(defn fib
  ([]
   (fib 1 1))
  ([a b]
   (lazy-seq (cons a (fib b (+ a b))))))

(def fib-10
  (take 10 (fib)))

(def fib-10-inc
  (map inc fib-10))

(map (comp inc inc) fib-10)

;; It comes with full-support for the [Nextjournal viewer api](https://nextjournal.com/help/clojure-viewer-api), for example `vega-lite`:
(def vega-unemployment-map
  ^{:nextjournal/viewer :vega-lite}
  {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                 :format {:type "topojson" :feature "counties"}}
   :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                    :key "id" :fields ["rate"]}}]
   :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

(count long-thing)

(v/html [:h1 "Ohai Hiccup 👋👋👋"])

;; We can opt out of caching by tagging a var with `^:clerk/no-cache` metadata.
(def ^:clerk/no-cache random-thing
  (rand-int 1000))

;; Other examples of viewers are `plotly` & `latex`.
(def plotly
  ^{:nextjournal/viewer :plotly}
  {:data [{:y (shuffle (range 10)) :name "The Federation"}
          {:y (shuffle (range 10)) :name "The Empire"}]})

(def formula
  {:nextjournal/viewer :latex
   :nextjournal/value "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}"})

(do ;; slow as well
  (Thread/sleep 3000)
  42)

(def random-cached-thing
  (rand-int 1000))
