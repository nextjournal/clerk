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
  [:div.bg-slate-500.rounded-full.text-white.text-xs.w-7.h-7.flex.items-center.justify-center.flex-shrink-0
   kind])

;; ## Display

;; This section provides a recursive function to show the nested the
;; code with results (currently in a visually unappealing way, but it
;; should be enough to get you started. 😊)

(defn show-element [lookup depth result-id elem]
  (println elem)
  (cond (and (list? elem) (= (first elem) 'add-trace)) [:div.mt-2
                                                        (show-element lookup depth (second (second elem)) (second (nth elem 2)))]
        (and (list? elem) (= (first elem) 'let)) [:div.rounded-md.p-2.mt-2
                                                  {:class (if (even? depth) "bg-slate-200 " "bg-slate-300 ")}
                                                  [:span.font-bold "let"]
                                                  (let [depth (inc depth)]
                                                    [:div.rounded-md.p-2.mt-2
                                                     {:class (if (even? depth) "bg-slate-200 " "bg-slate-300 ")}
                                                     [:div.flex
                                                      (icon "[]")
                                                      (into [:div.ml-3]
                                                            (map-indexed
                                                              (fn [i [k v]]
                                                                [:div.flex
                                                                 {:class (if (zero? i) "mt-1" "mt-2")}
                                                                 [:div.mr-3.font-bold (show-element lookup (inc depth) nil k)]
                                                                 [:div {:class (when (coll? v) "-mt-4 mb-2")}
                                                                  (show-element lookup (inc depth) nil v)]])
                                                              (->> elem second (partition 2))))]])
                                                  (into [:div]
                                                        (map
                                                          (fn [el]
                                                            (show-element lookup (inc depth) nil el))
                                                          (drop 2 elem)))]
        (map? elem) [:div.rounded-md.p-2.flex.mt-2
                     {:class (if (even? depth) "bg-slate-200 " "bg-slate-300 ")}
                     (icon "{}")
                     (into [:div.ml-3]
                           (let [key-length (max (map #(-> % name count) (keys elem)))]
                             (mapv (fn [[k v]]
                                     [:div.flex.items-center
                                      [:div.mr-3.font-bold (show-element lookup (inc depth) nil k)]
                                      [:div {:class (if (list? v) "-mt-2")}
                                       (show-element lookup (inc depth) nil v)]])
                                   elem)))]
        (sequential? elem) [:div.rounded-md.p-2.flex
                            {:class (str (if (even? depth) "bg-slate-200 " "bg-slate-300 ")
                                         (when-not (list? elem) "mt-2"))}
                            (when-not (list? elem)
                              (icon "[]"))
                            [:div.flex-auto
                             {:class (when-not (list? elem)
                                       "ml-3 mt-1")}
                             [:div
                              (into [:<>]
                                    (mapv (partial show-element lookup (inc depth) nil) elem))
                              (when result-id
                                (let [result (get lookup result-id)]
                                  (if-let [e (:exception result)]
                                    [:div.rounded.border-2.border-red-500.bg-red-100.text-red-500.p-2.font-bold.text-xs.mt-2 e]
                                    [:span.text-slate-500.text-sm.float-right.ml-3
                                     (str "→ " (pr-str (get lookup result-id)))])))]]]
        :else (str " "
                   (if (string? elem) "\"")
                   elem
                   (if (string? elem) "\""))))

(clerk/html
  [:div.text-sm {:class "font-mono"}
   ;; boring arithmetic example form
   (let [t (debug-expression '(let [x 10
                                    y (/ 20 "foo")
                                    vs [3 1 4 1 5]
                                    tab {:a 4
                                         :b (+ 3 3)
                                         :c 8}]
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