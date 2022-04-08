;; # 👩🏻‍💻 Show the code
^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns tracer
  (:require [nextjournal.clerk :as clerk]
            [clojure.string :as str]))

;; ## Tracer

;; This is a minimal implementation of the code to trace a quoted form
;; and record the result from evaluating each sub-expresison. It
;; currently returns the data in a somewhat awkward shape. How would
;; you improve the API?

(def ^:dynamic *trace-accumulator* nil)

(defn add-trace [id form result]
  (swap! *trace-accumulator* conj [id [form result]])
  result)

(defn debug-expression [quoted-expr]
  (let [trace (atom [])]
    (binding [*trace-accumulator* trace]
      (eval
        (clojure.walk/postwalk
          (fn [form]
            (if (list? form)
              `(add-trace '~(gensym) '~form (try ~form (catch Exception ~(symbol "e") {:exception (.getMessage ~(symbol "e"))})))
              form))
          quoted-expr)))
    @trace))

^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(defn icon [kind]
  [:div.text-slate-700
   {:style {:font-size 10}}
   kind])

;; ## Display

;; This section provides a recursive function to show the nested the
;; code with results (currently in a visually unappealing way, but it
;; should be enough to get you started. 😊)

^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(declare show-element)

^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(def exception-icon
  [:svg.h-4.w-4
   {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"}
   [:path {:fill-rule "evenodd" :d "M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" :clip-rule "evenodd"}]])

^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(defn show-exception [e]
  [:div.text-red-600.flex.text-xs
   exception-icon
   (when (<= (count e) 150)
     [:span.ml-1 e])])

^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(defn show-result [result]
  (let [res (str (pr-str result))]
    [:div.whitespace-nowrap.text-slate-500.text-xs
     [:span.mx-1 "→"]
     (if (str/starts-with? res "#function")
       "\uD835\uDC53"
       res)]))



^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(defn show-with-bindings
      ([lookup depth result-id elem]
       (show-with-bindings true lookup depth result-id elem))
  ([as-pairs? lookup depth result-id elem]
   (println elem)
   (let [named? (-> elem second symbol?)
         bindings (nth elem (if named? 2 1))
         bindings-empty? (empty? bindings)
         remainder (drop (if named? 3 2) elem)]
     [:div.inline-block.relative
      [:div.flex
       [:div.relative
        [:div.absolute.bg-white.border-2.border-r-0.left-0.top-0.bottom-0.rounded
         {:style {:z-index -1 :right -20}}]
        [:div.pl-2.font-bold (first elem)]]
       (when named?
         [:div.ml-2 (second elem)])
       [:div.flex.ml-2.relative
        [:div.absolute.bg-white.border-2.left-0.top-0.bottom-0.rounded
         {#_#_:class (when-not bindings-empty? "border-r-0")
          :style {:z-index -1 :width (if bindings-empty? 30 40)}}]
        [:div.pl-2
         (icon "[]")]
        (when-not bindings-empty?
          (if as-pairs?
            (into [:div]
                  (map
                    (fn [[k v]]
                      [:div
                       [:div.inline-flex.px-2
                        [:div.font-bold.relative.px-2
                         [:div.absolute.bg-white.border-2.left-0.top-0.bottom-0.rounded
                          {:style {:z-index -1 :right -5}}]
                         (show-element lookup (inc depth) nil k)]
                        (show-element lookup (inc depth) nil v)]])
                    (partition 2 bindings)))
            (into [:div.flex]
                  (map
                    (fn [b]
                      [:div.mr-2 b])
                    bindings))))]]
      (into [:div.ml-4]
            (map
              (fn [el]
                (show-element lookup (inc (inc depth)) nil el))
              remainder))])))

^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(defn show-coll [lookup depth result-id elem]
  (let [empty? (empty? elem)]
    [:div.flex.relative.pl-2
     [:div.absolute.bg-white.border-2.left-0.top-0.bottom-0.rounded
      {:class (when-not empty? "border-r-0")
       :style {:z-index -1 :width (if empty? 30 40)}}]
     (icon (cond (set? elem) "#{}"
                 (map? elem) "{}"
                 (vector? elem) "[]"))
     [:div.flex-auto.ml-2
      (if (map? elem)
        (into [:div]
              (map
                (fn [[k v]]
                  [:div.flex
                   (show-element lookup (inc depth) nil k)
                   (show-element lookup (inc depth) nil v)])
                elem))
        (into [:div]
              (mapv (fn [e]
                      [:div
                       [:div.inline-flex
                        (show-element lookup (inc depth) nil e)]]) elem)))]]))

^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(defn show-seq [lookup depth result-id elem]
  (let [remaining (rest elem)
        line? (< (count remaining) 4)]
    [:div.inline-flex
     {:class (when line? "items-center ")}
     [:div.font-bold.px-1.relative
      [:div.absolute.bg-white.border-2.left-0.top-0.bottom-0.rounded
       {:class "border-r-0"
        :style {:z-index -1 :right -5}}]
      (show-element lookup (inc depth) nil (first elem))]
     [:div.flex-auto.items-center
      {:class (when line? "flex")}
      (into [:<>] (mapv
                    (fn [e]
                      [:div
                       (show-element lookup (inc depth) nil e)])
                    remaining))]
     (when result-id
       (let [result (get lookup result-id)]
         [:div.inline-flex.ml-2
          {:class (if line? "items-center" "mt-[2px]")
           :style {:max-width 300}}
          (if-let [e (:exception result)]
            (show-exception e)
            (show-result result))]))]))



^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(defn show-element [lookup depth result-id elem]
  (println elem)
  (let [list-elem? (list? elem)
        fn-elem? (and list-elem? (contains? #{'fn 'fn* 'defn} (first elem)))]
    (cond (and list-elem? (= (first elem) 'add-trace)) (show-element lookup depth (second (second elem)) (second (nth elem 2)))
          (and list-elem? (or (= (first elem) 'let) fn-elem?)) (show-with-bindings (not fn-elem?) lookup depth result-id elem)
          (and (not list-elem?) (coll? elem)) (show-coll lookup depth result-id elem)
          (sequential? elem) (show-seq lookup depth result-id elem)
          :else
          [:div.relative
           (when-not (symbol? elem) {:class "px-1"})
           (when-not (symbol? elem)
             [:div.absolute.bg-white.border-2.left-0.top-0.bottom-0.rounded
              {:style {:z-index -1 :right -5}}])
           (str " "
                (if (string? elem) "\"")
                elem
                (if (string? elem) "\""))])))
^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(defn offsets [xs]
  (reduce
    (fn [acc n]
      (conj acc (+ (last acc) n)))
    [0]
    xs))

(def char-width 11)
(def corner-radius 5)
(def box-min-width 20)
(def box-height 30)
(def box-inset-x 20)
(def text-inset-x 3)

(declare show-svg-element)

(defn show-let [lookup depth result-id elem]
  (let [children (concat
                   (map
                     (fn [[k v]]
                       (let [k (show-svg-element lookup (inc depth) nil k)
                             v (show-svg-element lookup (inc depth) nil v)
                             w (+ (:width k) (:width v))
                             h (max (:height k) (:height v))]
                         {:width w
                          :height h
                          :el [:g {}
                               [:rect {:width w
                                       :height h
                                       :fill (if (even? depth) "#fef9c3" "#fef089")
                                       :rx corner-radius
                                       :stroke "#bae6fc"}]
                               (:el k)
                               (assoc-in (:el v) [1 :transform] (str "translate(" (:width k) "," 0 ")"))]}))
                     (partition 2 (second elem)))
                   (map (partial show-svg-element lookup (inc depth) nil) (drop 2 elem)))
        label-width (+ text-inset-x (* (count "let") char-width) text-inset-x)
        max-width (+ (->> children (map :width) (apply (partial max box-min-width))) label-width)
        height (->> children (map :height) (apply +))]
    (when (and (sequential? elem) (= (first elem) 'let))
      (println children))
    {:width max-width
     :height height
     :el (into [:g {}
                [:rect {:width max-width
                        :height height
                        :fill (if (even? depth) "#f0f9ff" "#e0f2fe")
                        :rx corner-radius
                        :stroke "#bae6fc"}]
                [:text.font-mono.font-bold
                 {:x text-inset-x :y (- box-height (* box-height 0.33)) :fill "black"} "let"]]
               (map (fn [{:keys [el]} offset]
                      (assoc-in el [1 :transform] (str "translate(" (+ label-width box-inset-x) "," offset ")")))
                    children
                    (offsets (map :height children))))}))

^{::clerk/visibility :hide ::clerk/viewer clerk/hide-result}
(defn show-svg-element [lookup depth result-id elem]
  (cond
    (and (list? elem) (= (first elem) 'add-trace))
    (show-svg-element lookup depth (second (second elem)) (second (nth elem 2)))
    (and (list? elem) (= (first elem) 'let))
    (show-let lookup depth nil elem)
    (coll? elem)
    (let [children (map
                     (fn [e]
                       (show-svg-element lookup (inc depth) nil e))
                     elem)
          icon (cond
                 (and (not (map-entry? elem)) (vector? elem)) "[]"
                 (map? elem) "{}"
                 (set? elem) "#{}"
                 :else "")
          icon-width (+ (* 2 text-inset-x) (* (count icon) char-width))
          max-width (+ icon-width
                       (->> children (map :width) (apply (partial max box-min-width))))
          height (->> children (map :height) (apply +))]
      (when (and (sequential? elem) (= (first elem) 'let))
        (println children))
      {:width max-width
       :height height
       :el (into [:g {}
                  [:rect {:width max-width
                          :height height
                          :fill (if (even? depth) "#6ee7b7" "#35d399")
                          :rx corner-radius
                          :stroke "#bae6fc"}]
                  [:text.font-mono.text-xs
                   {:x text-inset-x :y (- box-height (* box-height 0.33)) :fill "black"} icon]]
                 (map (fn [{:keys [el]} offset]
                        (assoc-in el [1 :transform] (str "translate(" (+ text-inset-x box-inset-x) "," offset ")")))
                      children
                      (offsets (map :height children))))})
    :else
    (let [width (+ text-inset-x (* (count (str elem)) char-width) text-inset-x)]
      {:width width
       :height box-height
       :el [:g {}
            [:text.font-mono {:x text-inset-x :y (- box-height (* box-height 0.33)) :fill "black"}
             elem]]})))

^::clerk/no-cache
(clerk/html
  {::clerk/width :wide}
  [:svg
   {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 1000 1900"}
   ;; boring arithmetic example form
   (let [t1 (debug-expression '(let [x 10
                                     y (/ 20 0)
                                     vs [3 1 4 1 5]
                                     tab {:a 4
                                          :b (+ 3 3)
                                          :c 8}
                                     ordered #{5 1 2 :eight}
                                     a-fn (fn [] (println "Ohai 👋"))
                                     another-fn #(+ %1 %2)
                                     fn-returning-fn (fn []
                                                       (fn [] 1))]
                                 (+ (* x 5)
                                    (apply + vs)
                                    (:a tab)
                                    (- (/ y 2)
                                       (:b tab)
                                       3))))]
     (:el
       (show-svg-element
         ;; result id -> value lookup table
         (reduce (fn [m [k [_ v]]] (assoc m k v)) {} t1)
         ;; initial display depth
         0
         ;; id of top level form's result
         (first (last t1))
         ;; the top level form itself
         (first (second (last t1))))))])


#_(clerk/html
    {::clerk/width :wide}
    [:div.text-sm.relative {:class "font-mono"}
     (let [t2 (debug-expression '(defn wrapped-with-metadata [value visibility h]
                                   (cond-> {:nextjournal/value value
                                            ::visibility visibility}
                                           h (assoc :nextjournal/blob-id (cond-> h (not (string? h)) hash)))))]
       (show-element
         ;; result id -> value lookup table
         (reduce (fn [m [k [_ v]]] (assoc m k v)) {} t2)
         ;; initial display depth
         0
         ;; id of top level form's result
         (first (last t2))
         ;; the top level form itself
         (first (second (last t2)))))])
