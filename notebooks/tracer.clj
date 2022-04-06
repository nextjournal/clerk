;; # 👩🏻‍💻 Show the code
^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns tracer
  (:require [nextjournal.clerk :as clerk]))

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

(defn icon [kind]
  [:div.bg-slate-500.rounded-full.text-white.text-xs.w-5.h-5.flex.items-center.justify-center.flex-shrink-0
   {:style {:font-size 10}}
   kind])

;; ## Display

;; This section provides a recursive function to show the nested the
;; code with results (currently in a visually unappealing way, but it
;; should be enough to get you started. 😊)

(declare show-element)

(defn show-let [lookup depth result-id elem]
  [:div.rounded-md.p-2.pt-0
   {:class (if (even? depth) "bg-slate-200 " "bg-slate-300 ")}
   [:span.font-bold.inline-block.mt-2 "let"]
   (let [depth (inc depth)]
     [:div.rounded-md.p-2.pt-0.mt-2
      {:class (if (even? depth) "bg-slate-200 " "bg-slate-300 ")}
      [:div.flex
       [:div.mt-2 (icon "[]")]
       (into [:div.ml-2 {:class "-mt-2"}]
             (map
               (fn [[k v]]
                 [:div.flex.mt-2
                  [:div.mr-2.font-bold.flex-shrink-0
                   (show-element lookup (inc depth) nil k)]
                  [:div {:class (when (coll? v) "-mt-2")}
                   (show-element lookup (inc depth) nil v)]])
               (->> elem second (partition 2))))]])
   (into [:div]
         (map
           (fn [el]
             (show-element lookup (inc depth) nil el))
           (drop 2 elem)))])

(defn show-coll [lookup depth result-id elem]
  [:div.rounded-md.p-2.pt-0.flex.mt-2
   {:class (str (if (even? depth) "bg-slate-200 " "bg-slate-300 "))}
   [:div.mt-2 (icon (cond (set? elem) "#{}"
                          (map? elem) "{}"
                          (vector? elem) "[]"))]
   [:div.flex-auto.ml-2
    (if (map? elem)
      (into [:div]
            (map
              (fn [[k v]]
                [:div.flex
                 [:div.mr-2.font-bold.flex-shrink-0
                  (show-element lookup (inc depth) nil k)]
                 [:div {:class (when (coll? v) "-mt-2 -mb-2")}
                  (show-element lookup (inc depth) nil v)]])
              elem))
      (into [:div] (mapv (partial show-element lookup (inc depth) nil) elem)))]])

(defn show-seq [lookup depth result-id elem]
  [:div.rounded-md.p-2.pt-0.flex.mt-2
   {:class (str (if (even? depth) "bg-slate-200 " "bg-slate-300 "))}
   [:div
    [:span.font-bold (show-element lookup (inc depth) nil (first elem))]
    (into [:<>] (mapv (partial show-element lookup (inc depth) nil) (rest elem)))
    (when result-id
      (let [result (get lookup result-id)]
        (if-let [e (:exception result)]
          [:div.rounded.border-2.border-red-500.bg-red-100.text-red-500.p-2.font-bold.text-xs.mt-2 e]
          [:span.text-slate-500.float-right.ml-2.mt-2
           (str "→ " (pr-str result))])))]])

(defn show-element [lookup depth result-id elem]
  (println elem)
  (cond (and (list? elem) (= (first elem) 'add-trace)) (show-element lookup depth (second (second elem)) (second (nth elem 2)))
        (and (list? elem) (= (first elem) 'let)) (show-let lookup depth result-id elem)
        (and (not (list? elem)) (coll? elem)) (show-coll lookup depth result-id elem)
        (sequential? elem) (show-seq lookup depth result-id elem)
        :else
        [:span.inline-block.mt-2.mr-2
         (str " "
              (if (string? elem) "\"")
              elem
              (if (string? elem) "\""))]))

^::clerk/no-cache
(clerk/html
  [:div.text-sm {:class "font-mono"}
   ;; boring arithmetic example form
   (let [t (debug-expression '(let [x 10
                                    y (/ 20 0)
                                    vs [3 1 4 1 5]
                                    tab {:a 4
                                         :b (+ 3 3)
                                         :c 8}
                                    ordered #{5 1 2 :eight}
                                    a-fn (fn [] (println "Ohai 👋"))
                                    another-fn #(+ %1 %2)]
                                (+ (* x 5)
                                   (apply + vs)
                                   (:a tab)
                                   (- (/ y 2)
                                      (:b tab)
                                      3))))]
     (show-element
       ;; result id -> value lookup table
       (reduce (fn [m [k [_ v]]] (assoc m k v)) {} t)
       ;; initial display depth
       0
       ;; id of top level form's result
       (first (last t))
       ;; the top level form itself
       (first (second (last t)))))])