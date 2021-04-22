;; # Observator Demo!!!!!!
(ns observator.demo
  (:require [clojure.string :as str]
            [observator.lib :as obs.lib]))

;; **Dogfooding** the system while constructing it, I'll try to make a
;; little bit of literate commentary. This is *literate* programming.
(def vega-unemployment-map
  ^{:nextjournal/viewer :vega-lite}
  {:width 650 :height 400 :data {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                                 :format {:type "topojson" :feature "counties"}}
   :transform [{:lookup "id" :from {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                                    :key "id" :fields ["rate"]}}]
   :projection {:type "albersUsa"} :mark "geoshape" :encoding {:color {:field "rate" :type "quantitative"}}})

(defn fix-case [s]
  (obs.lib/fix-case s))

(def long-thing
  (do
    (Thread/sleep 1000)
    (take 40 (map fix-case (str/split-lines (slurp "/usr/share/dict/words"))))))

(count long-thing)

;; We can opt out of caching by tagging a var with `^:observator/no-cache` metadata.
(def ^:observator/no-cache random-thing
  (rand-int 1000))

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
