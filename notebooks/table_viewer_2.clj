;; # Table (encore)

;; Leaning into the viewer api to make the table viewer better.

^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache table-viewer-2
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :refer :all]))

^{:nextjournal.clerk/viewer :hide-result}
(defn update-table-viewers' [viewers]
  (-> viewers
      (update-viewers {(comp #{:elision} :name) #(assoc % :render-fn '(fn [{:as fetch-opts :keys [total offset unbounded?]}]
                                                                        (v/html
                                                                         [v/consume-view-context :fetch-fn (fn [fetch-fn]
                                                                                                             [:tr.border-t.dark:border-slate-700
                                                                                                              [:td.text-center.py-1
                                                                                                               {:col-span #_#_num-cols FIXME 100
                                                                                                                :class (if (fn? fetch-fn)
                                                                                                                         "bg-indigo-50 hover:bg-indigo-100 dark:bg-gray-800 dark:hover:bg-slate-700 cursor-pointer"
                                                                                                                         "text-gray-400 text-slate-500")
                                                                                                                :on-click (fn [_] (when (fn? fetch-fn)
                                                                                                                                    (fetch-fn fetch-opts)))}
                                                                                                               (- total offset) (when unbounded? "+") (if (fn? fetch-fn) " more…" " more elided")]])])))
                       (comp #{string?} :pred) #(assoc % :render-fn (quote v/string-viewer))
                       (comp #{number?} :pred) #(assoc % :render-fn '(fn [x] (v/html [:span.tabular-nums (if (js/Number.isNaN x) "NaN" (str x))])))})
      (add-viewers [{:name :table/markup :render-fn '(fn [head+body opts]
                                                       (v/html (into [:table.text-xs.sans-serif.text-gray-900.dark:text-white.not-prose] (v/inspect-children opts) head+body)))}
                    {:name :table/head :render-fn '(fn [header-row {:as opts :keys [path]}]
                                                     (v/html [:thead.border-b.border-gray-300.dark:border-slate-700
                                                              (into [:tr]
                                                                    (map-indexed (fn [i {v :nextjournal/value}]
                                                                                   ;; TODO: consider not discarding viewer here
                                                                                   (let [title (str (cond-> v (keyword? v) name))]
                                                                                     [:th.relative.pl-6.pr-2.py-1.align-bottom.font-medium
                                                                                      ;; TODO: add column types to table normalization
                                                                                      {#_#_:class (if (number? (get-in rows [0 i])) "text-right" "text-left")
                                                                                       :title title}
                                                                                      [:div.flex.items-center title]]))) header-row)]))}
                    {:name :table/body :fetch-opts {:n 20} :render-fn '(fn [rows opts] (v/html [:tbody
                                                                                               (into [:<>] (map-indexed (fn [idx row] (v/inspect (update opts :path conj idx) row))) rows)]))}
                    {:name :table/row :render-fn '(fn [row {:as opts :keys [path]}]
                                                    (v/html (into [:tr.hover:bg-gray-200.dark:hover:bg-slate-700
                                                                   {:class (if (even? (peek path)) "bg-black/5 dark:bg-gray-800" "bg-white dark:bg-gray-900")}]
                                                                  (map (fn [cell] [:td.pl-6.pr-2.py-1 (v/inspect opts cell)])) row)))}
                    {:pred #{:nextjournal/missing} :render-fn '(fn [x] (v/html [:<>]))}])))

^{:nextjournal.clerk/viewer :hide-result}
(def my-table
  (partial with-viewer {:transform-fn (fn [{:as wrapped-value :nextjournal/keys [viewers] :keys [offset path current-path]}]
                                        (if-let [{:keys [head rows]} (normalize-table-data (->value wrapped-value))]
                                          (let [viewers (update-table-viewers' viewers)]
                                            (-> wrapped-value
                                                (assoc :nextjournal/viewer :table/markup)
                                                (update :nextjournal/width #(or % :wide))
                                                (assoc :nextjournal/viewers viewers)
                                                (assoc :nextjournal/value (cond->> [(with-viewer :table/body {::clerk/viewers viewers} (map (partial with-viewer :table/row {::clerk/viewers viewers}) rows))]
                                                                            head (cons (with-viewer :table/head {::clerk/viewers viewers} head))))))
                                          (-> wrapped-value
                                              assoc-reduced
                                              (assoc :nextjournal/width :wide)
                                              (assoc :nextjournal/value [(describe wrapped-value)])
                                              (assoc :nextjournal/viewer {:render-fn 'v/table-error}))))}))


;; ## The simplest example, no header.

(my-table [[1 2] [3 4]])

(my-table {:head ["num" "foo"] :rows [[1 2] [3 4]]})

(my-table (map-indexed #(vector (inc %1) %2) (->> "https://gist.githubusercontent.com/wchargin/8927565/raw/d9783627c731268fb2935a731a618aa8e95cf465/words" slurp clojure.string/split-lines (take 30))))

;; ## Table Inside a Table
(my-table [[1 2] [3 (my-table [[4 5] [6 7]])]])

;; ## Table with an Image in it
(my-table [["an image"]
           [(javax.imageio.ImageIO/read (java.net.URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"))]])


;; ## An error
(my-table #{1 2 3})
