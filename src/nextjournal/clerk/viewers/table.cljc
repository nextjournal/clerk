(ns nextjournal.clerk.viewers.table
  (:require [clojure.string :as str]))

(defn rpad-vec [v length padding]
  (vec (take length (concat v (repeat padding)))))

(def missing-pred
  :nextjournal/missing)

(defn wrapped-value?
  "Tests if `x` is a map containing a `:nextjournal/value`."
  [x]
  (and (map? x) ;; can throw for `sorted-map`
       (try (contains? x :nextjournal/value)
            (catch #?(:clj Exception :cljs js/Error) _e false))))

(defn ->value
  "Takes `x` and returns the `:nextjournal/value` from it, or otherwise `x` unmodified."
  [x]
  (if (wrapped-value? x)
    (:nextjournal/value x)
    x))

(defn normalize-seq-of-seq [s]
  (let [max-count (count (apply max-key count s))]
    {:rows (mapv #(rpad-vec (->value %) max-count missing-pred) s)}))

(defn normalize-seq-of-map [s]
  (let [ks (->> s (mapcat keys) distinct vec)]
    {:head ks
     :rows (mapv (fn [m] (mapv #(get m % missing-pred) ks)) s)}))


(defn normalize-map-of-seq [m]
  (let [ks (-> m keys vec)
        m* (if (seq? (get m (first ks)))
             (reduce (fn [acc [k s]] (assoc acc k (vec s))) {} m)
             m)]
    {:head ks
     :rows (->> (range (count (val (apply max-key (comp count val) m*))))
                (mapv (fn [i] (mapv #(get-in m* [% i] missing-pred) ks))))}))

(defn normalize-seq-to-vec [{:keys [head rows]}]
  (cond-> {:rows (vec rows)}
          head (assoc :head (vec head))))

(defn use-headers [s]
  (let [{:as table :keys [rows]} (normalize-seq-of-seq s)]
    (-> table
        (assoc :head (first rows))
        (update :rows rest))))

(defn classified-type [x]
  (cond
    (string? x) 'string
    (number? x) 'number
    (boolean? x) 'boolean
    (keyword? x) 'keyword
    (symbol? x) 'symbol
    :else (type x)))

(comment
  (map classified-type ["a" "b" "a" "a" nil "" "c" "d" "d" :foo 'bar 1 1/3 1.2]))

(defn log2 [n]
  #?(:clj (/ (Math/log n) (Math/log 2))
     :cljs (js/Math.log2 n)))

(defn sturges [values]
  (-> (count values) log2 int inc))

(defn fixed-size-bins [min-value max-value bin-count]
  (let [size (/ (- max-value min-value) bin-count)]
    (vec
      (for [step (range (inc bin-count))]
        (+ min-value (* size step))))))

(defn binary-search [v target]
  (loop [low 0
         high (dec (count v))]
    (if (> low high)
      (- (inc low))
      (let [mid (quot (+ low high) 2)
            mid-val (v mid)]
        (cond (< mid-val target) (recur (inc mid) high)
              (< target mid-val) (recur low (dec mid))
              :else mid)))))

(defn histogram [xs]
  (let [xs-count (count xs)
        min-value (apply min xs)
        max-value (apply max xs)
        ;; Calculate number of bins assuming ~normal dist using Sturges’ Rule
        ;; TODO: Investigate Freedman–Diaconis
        thresholds (fixed-size-bins min-value max-value (sturges xs))
        groups (->> xs
                    (filter #(and (>= % min-value) (<= % max-value)))
                    ;; Remove trailing threshold number
                    (group-by #(->> % (binary-search (subvec thresholds 0 (dec (count thresholds)))) inc Math/abs dec)))]
    (map-indexed
      (fn [i [group-min group-max]]
        (let [group (or (groups i) [])
              group-count (count group)]
          {:range [group-min group-max]
           :count group-count
           :percentage (/ group-count xs-count)}))
      (partition 2 1 thresholds))))

(comment
  (histogram [1 1 1 5 2 2 0 0 1 40 51 21])
  (histogram [1]))

(defn categories [xs]
  (let [row-count (count xs)
        category (fn [label count] {:label label :count count :percentage (/ count row-count)})
        {:keys [rest unique empty]}
        (->> xs
             frequencies
             (reduce (fn [acc [label count]]
                       (cond
                         (nil? label) (update acc :empty (fnil inc 0))
                         (< 1 count) (update acc :rest #(conj % (category label count)))
                         :else (update acc :unique (fnil inc 0))))
                     {:rest []}))]

    (concat rest
            (when unique [(category :unique unique)])
            (when empty [(category :empty empty)]))))

(comment
  (categories ["a" "b" "a" "a" nil "" "c" "d" "d"]))

(defn compute-col-summary [xs]
  (let [continuous? (every? number? xs)
        dist (if continuous? (histogram xs) (categories xs))]
    (cond->
      {:continuous? continuous?
       :col-type (->> xs (remove nil?) (map classified-type) distinct (str/join ", "))
       :distribution dist}
      (not continuous?) (assoc :category-count (count dist)))))

(comment
  (compute-col-summary ["a" "b" "a" "a" nil "" "c" "d" "d" :foo])
  (compute-col-summary [1 1 1 5 2 2 0 0 1 40 51 21]))

(defn transpose [rows]
  (apply mapv vector rows))

(defn compute-table-summary [{:as data :keys [head rows]}]
  (cond-> data
    head (assoc :summary
                (reduce (fn [acc [k xs]]
                          (assoc acc k (compute-col-summary xs)))
                  {}
                  (->> rows transpose (map vector head))))))

(comment
  (compute-table-summary {:head [:a :b :c] :rows [["roses" 2 1] ["are" 3 3] ["red" 4 1]]}))

(defn normalize-table-data [data]
  (->>
    (cond
      (and (map? data) (-> data :rows sequential?)) (normalize-seq-to-vec data)
      (and (map? data) (sequential? (first (vals data)))) (normalize-map-of-seq data)
      (and (sequential? data) (map? (first data))) (normalize-seq-of-map data)
      (and (sequential? data) (sequential? (first data))) (normalize-seq-of-seq data)
      :else nil)
    compute-table-summary))
