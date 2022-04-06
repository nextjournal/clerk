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

(defn icon [kind]
  [:div.text-slate-700
   {:style {:font-size 10}}
   kind])

;; ## Display

;; This section provides a recursive function to show the nested the
;; code with results (currently in a visually unappealing way, but it
;; should be enough to get you started. 😊)

(declare show-element)

(def exception-icon
  [:svg.h-4.w-4
   {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"}
   [:path {:fill-rule "evenodd" :d "M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" :clip-rule "evenodd"}]])

(defn show-exception [e]
  [:div.text-red-600.flex.text-xs
   exception-icon
   (when (<= (count e) 150)
     [:span.ml-1 e])])

(defn show-result [result]
  (let [res (str (pr-str result))]
    [:div.whitespace-nowrap.text-slate-500.text-xs
     [:span.mx-1 "→"]
     (if (str/starts-with? res "#function")
       "\uD835\uDC53"
       res)]))

(defn show-with-bindings
  ([lookup depth result-id elem]
   (show-with-bindings true lookup depth result-id elem))
  ([as-pairs? lookup depth result-id elem]
   [:div.pl-2.rounded.inline-block.shadow.border.border-slate-400.border-opacity-60
    {:class (str "py-[2px] pr-[2px] " (if (even? depth) "bg-slate-300" "bg-slate-200"))}
    (let [depth (inc depth)]
      [:<>
       [:div.flex
        [:div.mr-2.font-bold
         {:class "py-[2px] "}
         (first elem)]
        [:div
         [:div.px-2.rounded.relative.shadow.border.border-slate-400.border-opacity-60.mb-1
          {:class (str "py-[2px] " (if (even? depth) "bg-slate-300" "bg-slate-200"))}
          [:div.flex
           (icon "[]")
           (when-not (-> elem second empty?)
             (into [:div.ml-2]
                   (map
                     (fn [[k v]]
                       [:div.flex.mb-1.last:mb-0
                        [:div.mr-2.flex-shrink-0
                         {:class (when as-pairs? "font-bold")}
                         (show-element lookup (inc depth) nil k)]
                        [:div
                         (show-element lookup (inc depth) nil v)]])
                     (->> elem second (partition 2)))))]]]]
       (into [:div]
             (map
               (fn [el]
                 (show-element lookup (inc (inc depth)) nil el))
               (drop 2 elem)))])]))

(defn show-coll [lookup depth result-id elem]
  [:div.px-2.rounded.inline-flex.shadow.border.border-slate-400.border-opacity-60
   {:class (if (even? depth) "bg-slate-300" "bg-slate-200")}
   (icon (cond (set? elem) "#{}"
                    (map? elem) "{}"
                    (vector? elem) "[]"))
   [:div.flex-auto.ml-2
    (if (map? elem)
      (into [:div]
            (map
              (fn [[k v]]
                [:div.flex
                 [:div.mr-2.font-bold.flex-shrink-0
                  (show-element lookup (inc depth) nil k)]
                 [:div (show-element lookup (inc depth) nil v)]])
              elem))
      (into [:div] (mapv (partial show-element lookup (inc depth) nil) elem)))]])

(defn show-seq [lookup depth result-id elem]
  (let [remaining (rest elem)
        line? (< (count remaining) 4)]
    [:div.px-2.rounded.inline-flex.shadow.border.border-slate-400.border-opacity-60.mr-2.last:mr-0.flex-wrap
     {:class (str
               (when line? "items-center ")
               (if (even? depth) "bg-slate-300" "bg-slate-200 py-[2px]"))}
     [:div.font-bold.mr-2 (show-element lookup (inc depth) nil (first elem))]
     [:div.flex-auto.items-center
      {:class (when line? "flex")}
      (into [:<>] (mapv
                    (fn [e]
                      [:div
                       {:class (if line? "mr-2 last:mr-0" "mb-1 last:mb-0")}
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

(defn show-element [lookup depth result-id elem]
  (let [list-elem? (list? elem)
        fn-elem? (and list-elem? (contains? #{'fn 'fn*} (first elem)))]
    (cond (and list-elem? (= (first elem) 'add-trace)) (show-element lookup depth (second (second elem)) (second (nth elem 2)))
          (and list-elem? (or (= (first elem) 'let) fn-elem?)) (show-with-bindings (not fn-elem?) lookup depth result-id elem)
          (and (not list-elem?) (coll? elem)) (show-coll lookup depth result-id elem)
          (sequential? elem) (show-seq lookup depth result-id elem)
          :else
          [:span
           (str " "
                (if (string? elem) "\"")
                elem
                (if (string? elem) "\""))])))

^::clerk/no-cache
(clerk/html
  {::clerk/width :wide}
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