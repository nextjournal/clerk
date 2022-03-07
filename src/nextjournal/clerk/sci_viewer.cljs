(ns nextjournal.clerk.sci-viewer
  (:require [applied-science.js-interop :as j]
            [cljs.reader]
            [clojure.string :as str]
            [edamame.core :as edamame]
            [goog.object]
            [goog.string :as gstring]
            [nextjournal.clerk.viewer :as viewer :refer [code html md plotly tex vl with-viewer with-viewers]]
            [nextjournal.devcards :as dc]
            [nextjournal.markdown.transform :as md.transform]
            [nextjournal.sci-configs.js-interop :as sci-configs.js-interop]
            [nextjournal.sci-configs.reagent :as sci-configs.reagent]
            [nextjournal.ui.components.icon :as icon]
            [nextjournal.ui.components.d3-require :as d3-require]
            [nextjournal.view.context :as view-context]
            [nextjournal.viewer.code :as code]
            [nextjournal.viewer.katex :as katex]
            [nextjournal.viewer.markdown :as markdown]
            [nextjournal.viewer.mathjax :as mathjax]
            [nextjournal.viewer.plotly :as plotly]
            [nextjournal.viewer.vega-lite :as vega-lite]
            [re-frame.context :as rf]
            [react :as react]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.ratom :as ratom]
            [sci.core :as sci]))

(defn color-classes [selected?]
  {:value-color (if selected? "white-90" "dark-green")
   :symbol-color (if selected? "white-90" "dark-blue")
   :prefix-color (if selected? "white-50" "black-30")
   :label-color (if selected? "white-90" "black-60")
   :badge-background-color (if selected? "bg-white-20" "bg-black-10")})


(declare inspect)

(defn value-of
  "Safe access to a value at key a js object.

   Returns `'forbidden` if reading the property would result in a `SecurityError`.
   https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy"
  [obj k]
  (try
    (let [v (j/get obj k)]
      (.-constructor v) ;; test for SecurityError
      v)
    (catch js/Error ^js _
      'forbidden)))

(defn obj->clj [x]
  (-> (fn [result key]
        (let [v (aget x key)]
          (if (= "function" (goog/typeOf v))
            result
            (assoc result key v))))
      (reduce {} (goog.object/getKeys x))))

(def nbsp
  (gstring/unescapeEntities "&nbsp;"))

(defn js-object-viewer [x {:as opts :keys [!expanded-at path]}]
  (let [x' (obj->clj x)
        expanded? (@!expanded-at path)]
    (html [:span.inspected-value.whitespace-nowrap "#js {"
           (into [:<>]
                 (comp (map-indexed (fn [idx k]
                                      [:<>
                                       [inspect k (update opts :path conj idx)]
                                       " "
                                       [inspect (value-of x k) (update opts :path conj idx)]]))
                       (interpose (if expanded? [:<> [:br] (repeat (inc (count path)) " ")] " ")))
                 (keys x')) "}"])))

(defn render-toc [{:keys [toc]}]
  ;; TODO: delegate to n.clerk.viewer
  (r/with-let [collapsed? (r/atom true)]
    [:div.viewer-markdown.fixed.top-2.left-2.border.rounded-md.px-4.py-2
     [:a {:href "#" :on-click #(do (.preventDefault %) (swap! collapsed? not))} [icon/menu]]
     (when-not @collapsed?
       (md.transform/->hiccup markdown/default-renderers toc))]))

(defn notebook [{:as doc xs :blocks :keys [toc]}]
  (html
   (into [:div.flex.flex-col.items-center.viewer-notebook
          (when toc (render-toc doc))]
         (map (fn [x]
                (let [viewer (viewer/viewer x)
                      blob-id (:blob-id (viewer/value x))
                      prose? (= viewer :markdown)
                      inner-viewer-name (some-> x viewer/value viewer/viewer :name)]
                  [:div {:class ["viewer" "overflow-x-auto"
                                 (when (keyword? viewer) (str "viewer-" (name viewer)))
                                 (when-not prose? "not-prose")
                                 (when inner-viewer-name (str "viewer-" (name inner-viewer-name)))
                                 (when (and prose? inner-viewer-name (not= inner-viewer-name :markdown)) "not-prose")
                                 (case (or (viewer/width x) (case viewer (:code :code-folded) :wide :prose))
                                   :wide "w-full max-w-wide"
                                   :full "w-full"
                                   "w-full max-w-prose px-8")]}
                   (cond-> [inspect x]
                     blob-id (with-meta {:key blob-id}))])))
         xs)))

(defonce !edamame-opts
  (atom {:all true
         :row-key :line
         :col-key :column
         :location? seq?
         :end-location false
         :read-cond :allow
         :readers {'file (partial with-viewer :file)
                   'object (partial with-viewer :object)
                   'viewer-fn viewer/->viewer-fn
                   'viewer-eval #(*eval* %)}
         :features #{:clj}}))

(defn ^:export read-string [s]
  (edamame/parse-string s @!edamame-opts))

(defn opts->query [opts]
  (->> opts
       (map #(update % 0 name))
       (map (partial str/join "="))
       (str/join "&")))

#_(opts->query {:s 10 :num 42})

(defn unreadable-edn [edn]
  (html [:span.inspected-value.whitespace-nowrap.cmt-default edn]))

(defn error-badge [& content]
  [:div.bg-red-50.rounded-sm.text-xs.text-red-400.px-2.py-1.items-center.sans-serif.inline-flex
   [:svg.h-4.w-4.text-red-400 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :aria-hidden "true"}
    [:path {:fill-rule "evenodd" :d "M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" :clip-rule "evenodd"}]]
   (into [:div.ml-2.font-bold] content)])

(defn error-boundary [!error & _]
  (r/create-class
   {:constructor (fn [_ _])
    :component-did-catch (fn [_ e _info]
                           (reset! !error e))
    :get-derived-state-from-error (fn [e]
                                    (reset! !error e)
                                    #js {})
    :reagent-render (fn [_error & children]
                      (if @!error
                        [:div.bg-red-100.px-6.py-4.rounded.text-xs
                         [:h4.mt-0.uppercase.text-xs.tracking-wide "Viewer Rendering Error"]
                         [:p.mt-3.font-medium "The following javascript error occurred rendering this result. Your browser console might contain more information."]
                         ;; TODO: fix react CORS error in dev
                         [:pre.mt-3.text-red-500.whitespace-pre-wrap (.-message @!error)]]
                        (into [:<>] children)))}))

(defn fetch! [{:keys [blob-id]} opts]
  #_(js/console.log :fetch! blob-id opts)
  (-> (js/fetch (str "_blob/" blob-id (when (seq opts)
                                        (str "?" (opts->query opts)))))
      (.then #(.text %))
      (.then #(try (read-string %)
                   (catch js/Error e
                     (js/console.error #js {:message "sci read error" :blob-id blob-id :code-string % :error e })
                     (unreadable-edn %))))))

(defn read-result [{:nextjournal/keys [edn string]}]
  (if edn
    (try
      (read-string edn)
      (catch js/Error e
        (js/console.error #js {:message "sci read error" :edn-string edn :error e})
        ;; TODO: a read error in a viewers `:render-fn` will also cause a read error currently
        ;; Can we be more helpful by surfacing the read error in a viewer?
        (unreadable-edn edn)))
    (unreadable-edn string)))

(defn result-viewer [{:as result :nextjournal/keys [fetch-opts hash]} _opts]
  (html (r/with-let [!hash (atom hash)
                     !error (atom nil)
                     !desc (r/atom (read-result result))
                     !fetch-opts (atom fetch-opts)
                     fetch-fn (when @!fetch-opts
                                (fn [opts]
                                  (.then (fetch! @!fetch-opts opts)
                                         (fn [more]
                                           (swap! !desc viewer/merge-descriptions more)))))]
          (when-not (= hash @!hash)
            ;; TODO: simplify
            (reset! !hash hash)
            (reset! !fetch-opts fetch-opts)
            (reset! !desc (read-result result))
            (reset! !error nil))
          [view-context/provide {:fetch-fn fetch-fn}
           [error-boundary !error [inspect @!desc]]]))
  )

(defn toggle-expanded [!expanded-at path event]
  (.preventDefault event)
  (.stopPropagation event)
  (swap! !expanded-at update path not))

(defn expandable? [xs]
  (< 1 (count xs)))


(defn inspect-children [opts]
  ;; TODO: move update function onto viewer
  (map-indexed (fn [idx x]
                 (inspect (update opts :path conj idx) x))))

(def expand-style
  ["cursor-pointer"
   "bg-indigo-50"
   "hover:bg-indigo-100"
   "border-b"
   "border-gray-400"
   "hover:border-gray-500"
   "dark:bg-slate-900"
   "dark:hover:bg-slate-700"
   "dark:border-slate-600"
   "dark:hover:border-slate-500"])

(defn coll-viewer [xs {:as opts :keys [path viewer !expanded-at]}]
  (html (let [expanded? (@!expanded-at path)
              {:keys [opening-paren closing-paren]} viewer]
          [:span.inspected-value.whitespace-nowrap
           {:class (when expanded? "inline-flex")}
           [:span
            [:span.bg-opacity-70.whitespace-nowrap
             (when (< 1 (count xs))
               {:on-click (partial toggle-expanded !expanded-at path)
                :class expand-style})
             opening-paren]
            (into [:<>]
                  (comp (inspect-children opts)
                        (interpose (if expanded? [:<> [:br] nbsp (when (= 2 (count opening-paren)) nbsp)] " ")))
                  xs)
            (cond->> closing-paren (list? closing-paren) (into [:<>]))]])))

(declare inspect-paginated)
(dc/defcard coll-viewer
  (into [:div]
        (for [coll [
                    {:foo (into #{} (range 3))}
                    {:foo {:bar (range 1000)}}
                    [1 [2]]
                    [[1] 2]
                    {:a "bar"  :c (range 10)}
                    {:a "bar"  :c (range 10) :d 1}
                    ]]
          [:div.mb-3.result-viewer
           [inspect-paginated coll]])))

(dc/defcard coll-viewer-simple
  "with a simple `inspect` and no `describe` we don't move closing parens to children"
  (into [:div]
        (for [coll [
                    {:foo (into #{} (range 3))}
                    {:foo {:bar (range 20)}}
                    [1 [2]]
                    [[1] 2]
                    {:a "bar"  :c (range 10)}
                    {:a "bar"  :c (range 10) :d 1}
                    ]]
          [:div.mb-3.result-viewer
           [inspect coll]])))

(defn elision-viewer [{:as fetch-opts :keys [remaining unbounded?]} _]
  (html [view-context/consume :fetch-fn
         (fn [fetch-fn]
           [:span.sans-serif.relative.whitespace-nowrap
            {:style {:border-radius 2 :padding (when (fn? fetch-fn) "1px 3px") :font-size 11 :top -1}
             :class (if (fn? fetch-fn)
                      "cursor-pointer bg-indigo-200 hover:bg-indigo-300 dark:bg-slate-700 dark:hover:bg-slate-600 text-gray-900 dark:text-white"
                      "text-gray-400 dark:text-slate-300")
             :on-click #(when (fn? fetch-fn)
                          (fetch-fn fetch-opts))} remaining (when unbounded? "+") (if (fn? fetch-fn) " more…" " more elided")])]))

(defn map-viewer [xs {:as opts :keys [path viewer !expanded-at] :or {path []}}]
  (html (let [expanded? (@!expanded-at path)
              {:keys [closing-paren]} viewer]
          [:span.inspected-value.whitespace-nowrap
           {:class (when expanded? "inline-flex")}
           [:span
            [:span.bg-opacity-70.whitespace-nowrap
             (when (expandable? xs)
               {:on-click (partial toggle-expanded !expanded-at path)
                :class expand-style})
             "{"]
            (into [:<>]
                  (comp (inspect-children opts)
                        (interpose (if expanded? [:<> [:br] nbsp #_(repeat (inc (count path)) nbsp)] " ")))
                  xs)
            (cond->> closing-paren (list? closing-paren) (into [:<>]))]])))

(defn string-viewer [s {:as opts :keys [path !expanded-at] :or {path []}}]
  (html
   (let [expanded? (@!expanded-at path)]
     (into [:span {:class (when expanded? "whitespace-pre")}]
           (map #(if (string? %)
                   (if expanded?
                     (into [:<>] (interpose "\n " (str/split-lines %)))
                     (into [:<>] (interpose [:span.text-slate-400 "↩︎"] (str/split-lines %))))
                   (inspect opts %)))
           (if (string? s) [s] s)))))

(defn quoted-string-viewer [s {:as opts :keys [path !expanded-at] :or {path []}}]
  (html [:span.cmt-string.inspected-value.whitespace-nowrap
         (if (some #(and (string? %) (str/includes? % "\n")) (if (string? s) [s] s))
           [:span.cursor-pointer {:class expand-style
                                  :on-click (partial toggle-expanded !expanded-at path)} "\""]
           [:span "\""])
         (viewer/value (string-viewer s opts)) "\""]))

(defn sort! [!sort i k]
  (let [{:keys [sort-key sort-order]} @!sort]
    (reset! !sort {:sort-index i
                   :sort-key k
                   :sort-order (if (= sort-key k) (if (= sort-order :asc) :desc :asc) :asc)})))

(defn sort-data [{:keys [sort-index sort-order]} {:as data :keys [head rows]}]
  (cond-> data
    head (assoc :rows (->> rows
                           (sort-by #(cond-> (get % sort-index)
                                       (string? val) str/lower-case)
                                    (if (= sort-order :asc) #(compare %1 %2) #(compare %2 %1)))
                           vec))))

(def x-icon
  [:svg.h-4.w-4 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"}
   [:path {:fill-rule "evenodd" :d "M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" :clip-rule "evenodd"}]])

(def check-icon
  [:svg.h-4.w-4 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"}
   [:path {:fill-rule "evenodd" :d "M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" :clip-rule "evenodd"}]])

(defn table-error [[data]]
  ;; currently boxing the value in a vector to retain the type info
  ;; TODO: find a better way to do this
  (html
   [:div.bg-red-100.dark:bg-slate-800.px-6.py-4.rounded-md.text-xs.dark:border-2.dark:border-red-400
    [:h4.mt-0.uppercase.text-xs.dark:text-red-400.tracking-wide "Table Error"]
    [:p.mt-4.font-medium "Clerk’s table viewer does not recognize the format of your data:"]
    [:div.mt-2.flex
     [:div.text-red-500.mr-2 x-icon]
     [inspect data]]
    [:p.mt-4.font-medium "Currently, the following formats are supported:"]
    [:div.mt-2.flex.items-center
     [:div.text-green-500.mr-2 check-icon]
     [inspect {:column-1 [1 2]
               :column-2 [3 4]}]]
    [:div.mt-2.flex.items-center
     [:div.text-green-500.mr-2 check-icon]
     [inspect [{:column-1 1 :column-2 3} {:column-1 2 :column-2 4}]]]
    [:div.mt-2.flex.items-center
     [:div.text-green-500.mr-2 check-icon]
     [inspect [[1 3] [2 4]]]]
    [:div.mt-2.flex.items-center
     [:div.text-green-500.mr-2 check-icon]
     [inspect {:head [:column-1 :column-2]
               :rows [[1 3] [2 4]]}]]]))

(defn table-viewer [data opts]
  (r/with-let [!sort (r/atom nil)]
    (if-let [error-data (and (:error data) (:ex-data data))]
      (table-error [error-data])
      (let [{:as srt :keys [sort-index sort-key sort-order]} @!sort]
        (html
         (let [{:keys [head rows]} (cond->> data sort-key (sort-data srt))
               num-cols (-> rows viewer/value first viewer/value count)]
           [:table.text-xs.sans-serif.text-gray-900.dark:text-white
            (when head
              [:thead.border-b.border-gray-300.dark:border-slate-700
               (into [:tr]
                     (map-indexed (fn [i k]
                                    [:th.relative.pl-6.pr-2.py-1.align-bottom.font-medium
                                     {:class (if (number? (get-in rows [0 i])) "text-right" "text-left")
                                      #_#_#_#_
                                      :style {:cursor "ns-resize"}
                                      :on-click #(sort! !sort i k)
                                      :title (if (or (string? k) (keyword? k)) (name k) (str k))}
                                     [:div.inline-flex
                                      ;; Truncate to available col width without growing the table
                                      [:div.table.table-fixed.w-full.flex-auto
                                       {:style {:margin-left -12}}
                                       [:div.truncate
                                        [:span.inline-flex.justify-center.items-center.relative
                                         {:style {:font-size 20 :width 10 :height 10 :bottom -2 :margin-right 2}}
                                         (when (= sort-key k)
                                           (if (= sort-order :asc) "▴" "▾"))]
                                        (if (or (string? k) (keyword? k)) (name k) [inspect k])]]]]) head))])
            (into [:tbody]
                  (map-indexed (fn [i row]
                                 (if (= :elision (-> row viewer/viewer :name))
                                   (let [{:as fetch-opts :keys [remaining unbounded?]} (viewer/value row)]
                                     [view-context/consume :fetch-fn
                                      (fn [fetch-fn]
                                        [:tr.border-t.dark:border-slate-700
                                         [:td.text-center.py-1
                                          {:col-span num-cols
                                           :class (if (fn? fetch-fn)
                                                    "bg-indigo-50 hover:bg-indigo-100 dark:bg-slate-800 dark:hover:bg-slate-700 cursor-pointer"
                                                    "text-gray-400 text-slate-500")
                                           :on-click #(when (fn? fetch-fn)
                                                        (fetch-fn fetch-opts))}
                                          remaining (when unbounded? "+") (if (fn? fetch-fn) " more…" " more elided")]])])
                                   (let [row (viewer/value row)]
                                     (into
                                      [:tr.hover:bg-gray-200.dark:hover:bg-slate-700
                                       {:class (if (even? i) "bg-black/5 dark:bg-slate-800" "bg-white dark:bg-slate-900")}]
                                      (map-indexed (fn [j d]
                                                     [:td.pl-6.pr-2.py-1
                                                      {:class [(when (number? d) "text-right")
                                                               (when (= j sort-index) "bg-black/5 dark:bg-slate-800")]}
                                                      [inspect (update opts :path conj i j) d]]) row))))) (viewer/value rows)))]))))))


(defn throwable-viewer [{:keys [via trace]}]
  (html
   [:div.w-screen.h-screen.overflow-y-auto.bg-gray-100.p-6.text-xs.monospace.flex.flex-col
    [:div.rounded-md.shadow-lg.border.border-gray-300.bg-white.max-w-6xl.mx-auto
     (into
      [:div]
      (map
       (fn [{:as _ex :keys [type message data _trace]}]
         [:div.p-4.bg-red-100.border-b.border-gray-300.rounded-t-md
          [:div.font-bold "Unhandled " type]
          [:div.font-bold.mt-1 message]
          [:div.mt-1 (pr-str data)]])
       via))
     [:div.py-6
      [:table.w-full
       (into [:tbody]
             (map (fn [[call _x file line]]
                    [:tr.hover:bg-red-100.leading-tight
                     [:td.text-right.px-6 file ":"]
                     [:td.text-right.pr-6 line]
                     [:td.py-1.pr-6 call]]))
             trace)]]]]))

(defn tagged-value [tag value]
  [:span.inspected-value.whitespace-nowrap
   [:span.cmt-meta tag] value])

(defn normalize-viewer [x]
  (if-let [viewer (-> x meta :nextjournal/viewer)]
    (with-viewer viewer x)
    x))

(def js-viewers
  [{:pred #(implements? IDeref %) :render-fn #(tagged-value (-> %1 type pr-str) (inspect (deref %1) %2))}
   {:pred goog/isObject :render-fn js-object-viewer}
   {:pred array? :render-fn (partial coll-viewer {:open [:<> [:span.cmt-meta "#js "] "["] :close "]"})}])


(defonce !doc (ratom/atom nil))
(defonce !error (ratom/atom nil))
(defonce !viewers viewer/!viewers)

(defn set-viewers! [scope viewers]
  #_(js/console.log :set-viewers! {:scope scope :viewers viewers})
  (swap! !viewers assoc scope (vec viewers))
  'set-viewers!)

(declare default-viewers)

(defn render-with-viewer [{:as opts :keys [viewers]} viewer value]
  #_(js/console.log :render-with-viewer {:value value :viewer viewer #_#_ :opts opts})
  (cond (or (fn? viewer) (viewer/viewer-fn? viewer))
        (viewer value opts)

        (and (map? viewer) (:render-fn viewer))
        (render-with-viewer opts (:render-fn viewer) value)

        (keyword? viewer)
        (if-let [{:keys [fetch-opts render-fn]} (viewer/find-named-viewer viewers viewer)]
          (if-not render-fn
            (html (error-badge "no render function for viewer named " (str viewer)))
            (render-fn value (assoc opts :fetch-opts fetch-opts)))
          (html (error-badge "cannot find viewer named " (str viewer))))

        :else
        (html (error-badge "unusable viewer `" (pr-str viewer) "`"))))

(defn inspect
  ([x]
   (r/with-let [!expanded-at (r/atom {})]
     [inspect {:!expanded-at !expanded-at} x]))
  ([{:as opts :keys [viewers]} x]
   (let [value (viewer/value x)
         {:as opts :keys [viewers]} (assoc opts :viewers (vec (concat (viewer/viewers x) viewers)))
         all-viewers (viewer/get-viewers (:scope @!doc) viewers)]
     (or (when (react/isValidElement value) value)
         ;; TODO find option to disable client-side viewer selection
         (when-let [viewer (or (viewer/viewer x)
                               (viewer/viewer (viewer/wrapped-with-viewer value all-viewers)))]
           (inspect opts (render-with-viewer (assoc opts :viewers all-viewers :viewer viewer)
                                             viewer
                                             value)))))))

(defn in-process-fetch [value opts]
  (.resolve js/Promise (viewer/describe value opts)))

(defn inspect-paginated [value]
  (r/with-let [!desc (r/atom (viewer/describe value))]
    [view-context/provide {:fetch-fn (fn [fetch-opts]
                                       (.then (in-process-fetch value fetch-opts)
                                              (fn [more]
                                                (swap! !desc viewer/merge-descriptions more))))}
     [inspect @!desc]]))

(dc/defcard inspect-paginated-one
  []
  [:div
   (when-let [value @(rf/subscribe [::blobs :map-1])]
     [inspect-paginated value])]
  {::blobs {:vector (vec (range 30))
            :vector-nested [1 [2] 3]
            :vector-nested-taco '[l [l [l [l [🌮] r] r] r] r]
            :list (range 30)
            :recursive-range (map range (range 100))
            :map-1 {:hello :world}
            :map-vec-val {:hello [:world]}
            :map (zipmap (range 30) (range 30))}})

(dc/defcard inspect-paginated-more
  "In process inspect based on description."
  []
  [:div
   (map (fn [[blob-id xs]]
          ^{:key blob-id}
          [:div
           [inspect-paginated xs]])
        @(rf/subscribe [::blobs]))]
  {::blobs (hash-map (random-uuid) (vec (range 30))
                     (random-uuid) (range 40)
                     (random-uuid) (zipmap (range 50) (range 50)))})

(rf/reg-sub ::blobs
            (fn [db [blob-key id]]
              (cond-> (get db blob-key)
                id (get id))))

(defn error-viewer [e]
  (viewer/with-viewer :code (pr-str e)))


(dc/defcard inspect-values
  (into [:div]
        (for [value [123
                     ##NaN
                     'symbol
                     ::keyword
                     "a string"
                     nil
                     true
                     false
                     {:some "map"}
                     #{:a :set}
                     '[vector of symbols]
                     '(:list :of :keywords)
                     #_#_#js {:js "object"}
                     #js ["a" "js" "array"]
                     #_(js/Date.)
                     (random-uuid)
                     (fn a-function [_foo])
                     #_#_#_#_
                     (atom "an atom")
                     ^{:nextjournal/tag 'object} ['clojure.lang.Atom 0x2c42b421 {:status :ready, :val 1}]
                     ^{:nextjournal/tag 'var} ['user/a {:foo :bar}]
                     ^{:nextjournal/tag 'object} ['clojure.lang.Ref 0x73aff8f1 {:status :ready, :val 1}]]]
          [:div.mb-3.result-viewer
           [:pre [:code.inspected-value (binding [*print-meta* true] (pr-str value))]] [:span.inspected-value " => "]
           [inspect value]])))


(declare inspect)

;; TODO
#_
(dc/defcard viewer-reagent-atom
  [inspect (r/atom {:hello :world})])

#_ ;; commented out because recursive window prop will cause a loop
(dc/defcard viewer-js-window []
  [inspect js/window])

(dc/defcard viewer-vega-lite
  [inspect (vl {:width 650
                :height 400
                :data
                {:url "https://vega.github.io/vega-datasets/data/us-10m.json"
                 :format
                 {:type "topojson" :feature "counties"}}
                :transform
                [{:lookup "id"
                  :from
                  {:data {:url "https://vega.github.io/vega-datasets/data/unemployment.tsv"}
                   :key "id"
                   :fields ["rate"]}}]
                :projection {:type "albersUsa"}
                :mark "geoshape"
                :encoding
                {:color {:field "rate" :type "quantitative"}}})])

(dc/defcard viewer-plolty
  [inspect (plotly
            {:data [{:y (shuffle (range 10)) :name "The Federation" }
                    {:y (shuffle (range 10)) :name "The Empire"}]})])

(dc/defcard viewer-latex
  [inspect (tex "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")])

(dc/defcard viewer-mathjax
  [inspect (with-viewer :mathjax
             "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")])

(dc/defcard viewer-markdown
  [inspect (md "### Hello Markdown\n\n- a bullet point")])

(dc/defcard viewer-code
  [inspect (code "(str (+ 1 2) \"some string\")")])

(dc/defcard viewer-hiccup
  [inspect (html [:h1 "Hello Hiccup 👋"])])

(dc/defcard viewer-reagent-component
  "A simple counter component in reagent using `reagent.core/with-let`."
  [inspect (with-viewer :reagent
             (fn []
               (r/with-let [c (r/atom 0)]
                 [:<>
                  [:h2 "Count: " @c]
                  [:button.rounded.bg-blue-500.text-white.py-2.px-4.font-bold.mr-2 {:on-click #(swap! c inc)} "increment"]
                  [:button.rounded.bg-blue-500.text-white.py-2.px-4.font-bold {:on-click #(swap! c dec)} "decrement"]])))])

;; TODO add svg viewer

(dc/defcard progress-bar
  "Show how to use a function as a viewer, supports both one and two artity versions."
  [:div
   [inspect (with-viewer
              #(html
                [:div.relative.pt-1
                 [:div.overflow-hidden.h-2.mb-4-text-xs.flex.rounded.bg-blue-200
                  [:div.shadow-none.flex.flex-col.text-center.whitespace-nowrap.text-white.bg-blue-500
                   {:style {:width (-> %
                                       (* 100)
                                       int
                                       (max 0)
                                       (min 100)
                                       (str "%"))}}]]])
              0.33)]
   [inspect (with-viewer
              (fn [v _opts] (html
                             [:div.relative.pt-1
                              [:div.overflow-hidden.h-2.mb-4-text-xs.flex.rounded.bg-blue-200
                               [:div.shadow-none.flex.flex-col.text-center.whitespace-nowrap.text-white.bg-blue-500
                                {:style {:width (-> v
                                                    (* 100)
                                                    int
                                                    (max 0)
                                                    (min 100)
                                                    (str "%"))}}]]]))
              0.35)]])



(defn root []
  [:<>
   [inspect @!doc]
   (when @!error
     [:div.fixed.top-0.left-0.w-full.h-full
      [inspect @!error]])])

(defn ^:export set-state [{:as state :keys [doc error]}]
  (doseq [cell (-> doc viewer/value :blocks)
          :when (viewer/registration? cell)
          :let [form (viewer/value cell)]]
    (*eval* form))
  (when (contains? state :doc)
    (reset! !doc doc))
  (reset! !error error)
  (when-some [title (-> doc viewer/value :title)]
    (set! (.-title js/document) title)))

(dc/defcard eval-viewer
  "Viewers that are lists are evaluated using sci."
  [inspect (with-viewer (viewer/->viewer-fn '(fn [x] (v/html [:h3 "Ohai, " x "! 👋"]))) "Hans")])


(dc/defcard notebook
  "Shows how to display a notebook document"
  [state]
  [inspect
   (with-viewer :clerk/notebook
     [(with-viewer :markdown "# Hello Markdown\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum velit nulla, sodales eu lorem ut, tincidunt consectetur diam. Donec in scelerisque risus. Suspendisse potenti. Nunc non hendrerit odio, at malesuada erat. Aenean rutrum quam sed velit mollis imperdiet. Sed lacinia quam eget tempor tempus. Mauris et leo ac odio condimentum facilisis eu sed nibh. Morbi sed est sit amet risus blandit ullam corper. Pellentesque nisi metus, feugiat sed velit ut, dignissim finibus urna.")
      [1 2 3 4]
      (code "(shuffle (range 10))")
      {:hello [0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9]}
      (md "# And some more\n And some more [markdown](https://daringfireball.net/projects/markdown/).")
      (code "(shuffle (range 10))")
      (md "## Some math \n This is a formula.")
      (tex
       "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")
      (plotly {:data [{:y (shuffle (range 10)) :name "The Federation"}
                      {:y (shuffle (range 10)) :name "The Empire"}]})])]
  {::dc/class "p-0"})


(def ^:dynamic *viewers* nil)

(dc/defcard inspect-rule-30-sci
  []
  [inspect {:path []
            :viewers
            [{:pred number?
              :render-fn (viewer/->viewer-fn '#(v/html [:div.inline-block {:style {:width 16 :height 16}
                                                                           :class (if (pos? %) "bg-black" "bg-white border-solid border-2 border-
black")}]))}
             {:pred vector? :render-fn (viewer/->viewer-fn '#(v/html (into [:div.flex.inline-flex] (v/inspect-children %2) %1)))}
             {:pred list? :render-fn (viewer/->viewer-fn '#(v/html (into [:div.flex.flex-col] (v/inspect-children %2) %1)))}]}
   '([0 1 0] [1 0 1])])

(dc/defcard clj-long
  []
  [inspect
   '({:verts [[-0.5 -0.5] [0.5 -0.5] [0.5 0.5] [-0.5 0.5]],
      :invert? true}
     {:verts
      [[-0.32999999999999996 -0.5]
       [-0.3383203922298239 -0.44746711095625896]
       [-0.36246711095625894 -0.40007650711027953]
       [-0.40007650711027953 -0.36246711095625894]
       [-0.4474671109562589 -0.3383203922298239]
       [-0.5 -0.32999999999999996]
       [-0.5525328890437411 -0.3383203922298239]
       [-0.5999234928897205 -0.36246711095625894]
       [-0.6375328890437411 -0.40007650711027953]
       [-0.6616796077701761 -0.4474671109562589]
       [-0.67 -0.5]
       [-0.6616796077701761 -0.5525328890437411]
       [-0.6375328890437411 -0.5999234928897205]
       [-0.5999234928897205 -0.6375328890437411]
       [-0.5525328890437411 -0.6616796077701761]
       [-0.5 -0.67]
       [-0.44746711095625896 -0.6616796077701761]
       [-0.4000765071102796 -0.6375328890437411]
       [-0.36246711095625894 -0.5999234928897205]
       [-0.3383203922298239 -0.5525328890437411]],
      :invert? true}
     {:verts
      [[0.67 0.5]
       [0.6616796077701761 0.5525328890437411]
       [0.6375328890437411 0.5999234928897205]
       [0.5999234928897205 0.6375328890437411]
       [0.5525328890437411 0.6616796077701761]
       [0.5 0.67]
       [0.44746711095625896 0.6616796077701761]
       [0.4000765071102796 0.6375328890437411]
       [0.36246711095625894 0.5999234928897205]
       [0.3383203922298239 0.5525328890437411]
       [0.32999999999999996 0.5]
       [0.3383203922298239 0.4474671109562589]
       [0.36246711095625894 0.4000765071102796]
       [0.40007650711027953 0.36246711095625894]
       [0.4474671109562589 0.3383203922298239]
       [0.49999999999999994 0.32999999999999996]
       [0.552532889043741 0.3383203922298239]
       [0.5999234928897204 0.36246711095625894]
       [0.6375328890437411 0.40007650711027953]
       [0.6616796077701761 0.4474671109562589]],
      :invert? true}
     {:verts
      [[-0.32999999999999996 0.5]
       [-0.3383203922298239 0.5525328890437411]
       [-0.36246711095625894 0.5999234928897205]
       [-0.40007650711027953 0.6375328890437411]
       [-0.4474671109562589 0.6616796077701761]
       [-0.5 0.67]
       [-0.5525328890437411 0.6616796077701761]
       [-0.5999234928897205 0.6375328890437411]
       [-0.6375328890437411 0.5999234928897205]
       [-0.6616796077701761 0.5525328890437411]
       [-0.67 0.5]
       [-0.6616796077701761 0.4474671109562589]
       [-0.6375328890437411 0.4000765071102796]
       [-0.5999234928897205 0.36246711095625894]
       [-0.5525328890437411 0.3383203922298239]
       [-0.5 0.32999999999999996]
       [-0.44746711095625896 0.3383203922298239]
       [-0.4000765071102796 0.36246711095625894]
       [-0.36246711095625894 0.40007650711027953]
       [-0.3383203922298239 0.4474671109562589]],
      :invert? true}
     {:verts
      [[0.67 -0.5]
       [0.6616796077701761 -0.44746711095625896]
       [0.6375328890437411 -0.40007650711027953]
       [0.5999234928897205 -0.36246711095625894]
       [0.5525328890437411 -0.3383203922298239]
       [0.5 -0.32999999999999996]
       [0.44746711095625896 -0.3383203922298239]
       [0.4000765071102796 -0.36246711095625894]
       [0.36246711095625894 -0.40007650711027953]
       [0.3383203922298239 -0.4474671109562589]
       [0.32999999999999996 -0.5]
       [0.3383203922298239 -0.5525328890437411]
       [0.36246711095625894 -0.5999234928897205]
       [0.40007650711027953 -0.6375328890437411]
       [0.4474671109562589 -0.6616796077701761]
       [0.49999999999999994 -0.67]
       [0.552532889043741 -0.6616796077701761]
       [0.5999234928897204 -0.6375328890437411]
       [0.6375328890437411 -0.5999234928897205]
       [0.6616796077701761 -0.5525328890437411]],
      :invert? true})])


(dc/defcard clj-small
  []
  (let [x '({:verts [[-0.5 -0.5] [0.5 -0.5] [0.5 0.5] [-0.5 0.5]],
             :invert? true}
            {:verts
             [[0.67 -0.5]
              [0.6616796077701761 -0.44746711095625896]
              [0.6375328890437411 -0.40007650711027953]],
             :invert? true})
        y '({:verts [[-0.5 -0.5] [0.5 -0.5] [0.5 0.5] [-0.5 0.5]],
             :invert? true})]
    [:<>
     [:div.mb-4
      [inspect '{1 ● 2 ■ 3 ▲}]]
     [:div.mb-4
      [inspect {[[[[1 2]]]] [1 2]}]]

     [:div
      {:style {:margin-right -12}}
      [:div.mb-4.overflow-x-hidden
       [inspect x]]]]))

(defn ^:export ^:dev/after-load mount []
  (when-let [el (js/document.getElementById "clerk")]
    (rdom/render [root] el)))

(dc/defcard table [state]
  [inspect (viewer/table @state)]
  {::dc/state [[1 2 "ab"]
               [4 5 "cd"]]})

(dc/defcard table-incomplete [state]
  [inspect (viewer/table @state)]
  {::dc/state [[1 2 3]
               [4]]})

(dc/defcard table-col-headers [state]
  [inspect (viewer/table @state)]
  {::dc/state {:a [1 2 3]
               :b [4 5 6]}})

(dc/defcard table-col-headers-incomplete [state]
  [inspect (viewer/table @state)]
  {::dc/state {:a [1 2 3]
               :b [4]}})

(dc/defcard table-row-headers [state]
  [inspect (viewer/table @state)]
  {::dc/state [{:a 1 :b 2 :c 3}
               {:a 4 :b 5 :c 6}]})

(dc/defcard table-row-headers-incomplete [state]
  [inspect (viewer/table @state)]
  {::dc/state [{:a 1 :b 2 :c 3}
               {:a 4}]})

(dc/defcard table-error [state]
  [inspect (viewer/table @state)]
  {::dc/state #{1 2 3 4}})

(dc/when-enabled
  (defn rand-int-seq [n to]
    (take n (repeatedly #(rand-int to)))))

(declare lazy-inspect-in-process)

(dc/defcard table-long [state]
  [inspect (with-viewer table-viewer @state)]
  {::dc/state (let [n 20]
                {:species (repeat n "Adelie")
                 :island (repeat n "Biscoe")
                 :culmen-length-mm (rand-int-seq n 50)
                 :culmen-depth-mm (rand-int-seq n 30)
                 :flipper-length-mm (rand-int-seq n 200)
                 :body-mass-g (rand-int-seq n 5000)
                 :sex (take n (repeatedly #(rand-nth [:female :male])))})})

(dc/defcard table-elided-string [state]
  [inspect-paginated (viewer/table @state)]
  {::dc/state (repeat 3 (map (comp str/join (partial repeat 200)) ["a" "b" "c"]))})

(dc/defcard table-paginated-map-of-seq [state]
  [:div
   (when-let [xs @(rf/subscribe [::blobs])]
     [inspect-paginated (viewer/table xs)])]
  {::blobs (let [n 60]
             {:species (repeat n "Adelie")
              :island (repeat n "Biscoe")
              :culmen-length-mm (rand-int-seq n 50)
              :culmen-depth-mm (rand-int-seq n 30)
              :flipper-length-mm (rand-int-seq n 200)
              :body-mass-g (rand-int-seq n 5000)
              :sex (take n (repeatedly #(rand-nth [:female :male])))})})

(dc/defcard table-paginated-vec [state]
  [:div
   (when-let [xs @(rf/subscribe [::blobs])]
     [inspect-paginated (viewer/table xs)])]
  {::blobs (mapv  #(conj %2 (str "#" (inc %1))) (range) (repeat 60 ["Adelie" "Biscoe" 50 30 200 5000 :female]))})

(defn find-named-viewer [viewers viewer-name]
  (get (into {} (map (juxt :name identity)) viewers) viewer-name))

(defn clerk-eval [form]
  (.ws_send ^js goog/global (pr-str form)))

(defn katex-viewer [tex-string]
  (html (katex/to-html-string tex-string)))

(defn html-viewer [markup]
  (if (string? markup)
    (html [:div {:dangerouslySetInnerHTML {:__html markup}}])
    (r/as-element markup)))

(defn reagent-viewer [x]
  (r/as-element (cond-> x (fn? x) vector)))

(def mathjax-viewer (comp normalize-viewer mathjax/viewer))
(def code-viewer (comp normalize-viewer code/viewer))
(def plotly-viewer (comp normalize-viewer plotly/viewer))
(def vega-lite-viewer (comp normalize-viewer vega-lite/viewer))

(defn markdown-viewer
  "Accept a markdown string or a structure from parsed markdown."
  [data]
  (cond
    (string? data)
    (markdown/viewer data)
    (and (map? data) (contains? data :content) (contains? data :type))
    (with-viewer :hiccup (md.transform/->hiccup markdown/default-renderers data))))

(def expand-icon
  [:svg {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :width 12 :height 12}
   [:path {:fill-rule "evenodd" :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" :clip-rule "evenodd"}]])

(defn foldable-code-viewer [code-string]
  (r/with-let [!hidden? (r/atom true)]
    (html (if @!hidden?
            [:div.w-full.max-w-wide.sans-serif {:style {:background "var(--gray-panel-color)"}}
             [:button.mx-auto.flex.items-center.rounded-sm.cursor-pointer.bg-indigo-200.hover:bg-indigo-300.leading-none
              {:style {:font-size "11px" :padding "1px 3px"}
               :on-click #(swap! !hidden? not)}
              expand-icon " Show code…"]]
            [:div.viewer-code.relative {:style {:margin-top 0}}
             [inspect (code-viewer code-string)]
             [:button.sans-serif.mx-auto.flex.items-center.rounded-t-sm.cursor-pointer.bg-indigo-200.hover:bg-indigo-300.leading-none.absolute.bottom-0
              {:style {:font-size "11px" :padding "1px 3px" :left "50%" :transform "translateX(-50%)"}
               :on-click #(swap! !hidden? not)}
              [:span {:style {:transform "rotate(180deg)"}} expand-icon] " Hide code…"]]))))


(defn url-for [{:as src :keys [blob-id]}]
  (if (string? src)
    src
    (str "/_blob/" blob-id (when-let [opts (seq (dissoc src :blob-id))]
                             (str "?" (opts->query opts))))))

(def ^{:doc "Stub implementation to be replaced during static site generation. Clerk is only serving one page currently."}
  doc-url
  (sci/new-var 'doc-url (fn [x] (str "#" x))))

(def sci-viewer-namespace
  {'html html-viewer
   'inspect inspect
   'result-viewer result-viewer
   'coll-viewer coll-viewer
   'map-viewer map-viewer
   'elision-viewer elision-viewer
   'tagged-value tagged-value
   'inspect-children inspect-children
   'set-viewers! set-viewers!
   'string-viewer string-viewer
   'quoted-string-viewer quoted-string-viewer
   'table-viewer table-viewer
   'table-error table-error
   'with-viewer with-viewer
   'with-viewers with-viewers
   'with-d3-require d3-require/with
   'clerk-eval clerk-eval

   'throwable-viewer throwable-viewer
   'notebook-viewer notebook
   'katex-viewer katex-viewer
   'mathjax-viewer mathjax-viewer
   'markdown-viewer markdown-viewer
   'code-viewer code-viewer
   'foldable-code-viewer foldable-code-viewer
   'plotly-viewer plotly-viewer
   'vega-lite-viewer vega-lite-viewer
   'reagent-viewer reagent-viewer

   'doc-url doc-url
   'url-for url-for})

(defonce !sci-ctx
  (atom (sci/init {:async? true
                   :disable-arity-checks true
                   :classes {'js goog/global
                             :allow :all}
                   :aliases {'j 'applied-science/js-interop
                             'reagent 'reagent.core
                             'v 'nextjournal.clerk.sci-viewer}
                   :namespaces (merge {'nextjournal.clerk.sci-viewer sci-viewer-namespace}
                                      sci-configs.js-interop/namespaces
                                      sci-configs.reagent/namespaces)})))


(defn eval-form [f]
  (sci/eval-form @!sci-ctx f))

(set! *eval* eval-form)

(swap! viewer/!viewers (fn [viewers]
                         (-> (into {} (map (juxt key (comp #(into [] (map viewer/process-render-fn) %)  val))) viewers)
                             (update :root concat js-viewers))))
