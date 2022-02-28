;; # Elements of Clerk
;; Like the idea of notebooks, but hate leaving your favorite editor? We present Clerk, a tool that enables a rich, local-first notebook experience using standard Clojure namespaces.
(ns elements
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.viewer :as-alias v]))

^{::clerk/viewer {:fetch-fn (fn [_ x] x)
                  :render-fn (clerk/render-fn [board]
                                (let [cell #(vector :div.inline-block
                                                    {:class (if (zero? %)
                                                              "bg-white border-solid border-2 border-black"
                                                              "bg-black")
                                                     :style {:width 16 :height 16}})
                                      row #(into [:div.flex.inline-flex] (map cell) %)]
                                  (v/html (into [:div.flex.flex-col] (map row) board))))}}
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
  (->> g1 (iterate evolve) (take 17)))

;; Clerk uses static analysis and a tiny bit of data flow to avoid needless recomputation.
(defn fix-case [s]
  (clojure.string/upper-case s))

(def long-thing
  (let [words-path "/usr/share/dict/words"]
    (Thread/sleep 1000)
    (take 400 (map fix-case (when (fs/exists? words-path) (str/split-lines (slurp words-path)))))))

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
(clerk/vl
 {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                :format {:type "topojson" :feature "counties"}}
  :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                   :key "id" :fields ["rate"]}}]
  :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

(count long-thing)

(clerk/html [:h1 "Ohai Hiccup 👋👋👋"])

;; We can opt out of caching by tagging a var with `^::clerk/no-cache` metadata.
(def ^::clerk/no-cache random-thing
  (rand-int 1000))

;; Other examples of viewers are `plotly` & `latex`.
(clerk/plotly
 {:data [{:y (shuffle (range 10)) :name "The Federation"}
         {:y (shuffle (range 10)) :name "The Empire"}]})

(clerk/tex "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")

(do ;; slow as well
  (Thread/sleep 3000)
  42)

(def random-cached-thing
  (rand-int 1000))
