;; # Introducing Observator 👋
;; Like the idea of notebooks, but hate leaving your favorite editor? We present Observator, a tool that enables a rich, local-first notebook experience using standard Clojure namespaces.
(ns observator.demo
  (:require [clojure.string :as str]
            [observator.lib :as obs.lib]))

;; Observator uses static analysis and a tiny bit of data flow to avoid needless recomputation.
(defn fix-case [s]
  (obs.lib/fix-case s))

(def long-thing
  (do
    (Thread/sleep 1000)
    (take 40 (map fix-case (str/split-lines (slurp "/usr/share/dict/words"))))))

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

;; We can opt out of caching by tagging a var with `^:observator/no-cache` metadata.
(def ^:observator/no-cache random-thing
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
  (Thread/sleep 1000)
  42)

(def random-cached-thing
  (rand-int 1000))
