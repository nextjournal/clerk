(ns nextjournal.clerk.sci-viewer
  (:require ["framer-motion" :as framer-motion]
            [applied-science.js-interop :as j]
            [cljs.reader]
            [clojure.string :as str]
            [edamame.core :as edamame]
            [goog.object]
            [goog.string :as gstring]
            [lambdaisland.uri.normalize :as uri.normalize]
            [nextjournal.clerk.viewer :as viewer :refer [code md plotly tex vl with-viewer with-viewers]]
            [nextjournal.devcards :as dc]
            [nextjournal.markdown.transform :as md.transform]
            [nextjournal.ui.components.d3-require :as d3-require]
            [nextjournal.ui.components.icon :as icon]
            [nextjournal.ui.components.localstorage :as ls]
            [nextjournal.ui.components.motion :as motion]
            [nextjournal.ui.components.navbar :as navbar]
            [nextjournal.view.context :as view-context]
            [nextjournal.viewer.code :as code]
            [nextjournal.viewer.katex :as katex]
            [nextjournal.viewer.mathjax :as mathjax]
            [nextjournal.viewer.plotly :as plotly]
            [nextjournal.viewer.vega-lite :as vega-lite]
            [re-frame.context :as rf]
            [react :as react]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.ratom :as ratom]
            [sci.configs.applied-science.js-interop :as sci.configs.js-interop]
            [sci.configs.reagent.reagent :as sci.configs.reagent]
            [sci.core :as sci]))

(defn color-classes [selected?]
  {:value-color (if selected? "white-90" "dark-green")
   :symbol-color (if selected? "white-90" "dark-blue")
   :prefix-color (if selected? "white-50" "black-30")
   :label-color (if selected? "white-90" "black-60")
   :badge-background-color (if selected? "bg-white-20" "bg-black-10")})

(declare inspect inspect-paginated reagent-viewer)

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

(declare html html-viewer)

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

(defn toc-items [items]
  (reduce
    (fn [acc {:as item :keys [content children]}]
      (if content
        (let [title (md.transform/->text item)]
          (->> {:title title
                :path (str "#" (uri.normalize/normalize-fragment title))
                :items (toc-items children)}
               (conj acc)
               vec))
        (toc-items (:children item))))
    []
    items))

(defn dark-mode-toggle [!state]
  (let [{:keys [dark-mode?]} @!state
        spring {:type :spring :stiffness 200 :damping 10}]
    [:div.relative
     [:button.text-slate-400.hover:text-slate-600.dark:hover:text-white.cursor-pointer
      {:on-click #(swap! !state assoc :dark-mode? (not dark-mode?))}
      (if dark-mode?
        [:> motion/svg
         {:xmlns "http://www.w3.org/2000/svg"
          :class "w-5 h-5 md:w-4 md:h-4"
          :viewBox "0 0 50 50"
          :key "moon"}
         [:> motion/path
          {:d "M 43.81 29.354 C 43.688 28.958 43.413 28.626 43.046 28.432 C 42.679 28.238 42.251 28.198 41.854 28.321 C 36.161 29.886 30.067 28.272 25.894 24.096 C 21.722 19.92 20.113 13.824 21.683 8.133 C 21.848 7.582 21.697 6.985 21.29 6.578 C 20.884 6.172 20.287 6.022 19.736 6.187 C 10.659 8.728 4.691 17.389 5.55 26.776 C 6.408 36.163 13.847 43.598 23.235 44.451 C 32.622 45.304 41.28 39.332 43.816 30.253 C 43.902 29.96 43.9 29.647 43.81 29.354 Z"
           :fill "currentColor"
           :initial "initial"
           :animate "animate"
           :variants {:initial {:scale 0.6 :rotate 90}
                      :animate {:scale 1 :rotate 0 :transition spring}}}]]
        [:> motion/svg
         {:key "sun"
          :class "w-5 h-5 md:w-4 md:h-4"
          :viewBox "0 0 24 24"
          :fill "none"
          :xmlns "http://www.w3.org/2000/svg"}
         [:> motion/circle
          {:cx "11.9998"
           :cy "11.9998"
           :r "5.75375"
           :fill "currentColor"
           :initial "initial"
           :animate "animate"
           :variants {:initial {:scale 1.5}
                      :animate {:scale 1 :transition spring}}}]
         [:> motion/g
          {:initial "initial"
           :animate "animate"
           :variants {:initial {:rotate 45}
                      :animate {:rotate 0 :transition spring}}}
          [:circle {:cx "3.08982" :cy "6.85502" :r "1.71143" :transform "rotate(-60 3.08982 6.85502)" :fill "currentColor"}]
          [:circle {:cx "3.0903" :cy "17.1436" :r "1.71143" :transform "rotate(-120 3.0903 17.1436)" :fill "currentColor"}]
          [:circle {:cx "12" :cy "22.2881" :r "1.71143" :fill "currentColor"}]
          [:circle {:cx "20.9101" :cy "17.1436" :r "1.71143" :transform "rotate(-60 20.9101 17.1436)" :fill "currentColor"}]
          [:circle {:cx "20.9101" :cy "6.8555" :r "1.71143" :transform "rotate(-120 20.9101 6.8555)" :fill "currentColor"}]
          [:circle {:cx "12" :cy "1.71143" :r "1.71143" :fill "currentColor"}]]])]]))

(def local-storage-dark-mode-key "clerk-darkmode")

(defn set-dark-mode! [dark-mode?]
  (let [class-list (.-classList (js/document.querySelector "html"))]
    (if dark-mode?
      (.add class-list "dark")
      (.remove class-list "dark")))
  (ls/set-item! local-storage-dark-mode-key dark-mode?))

(defn setup-dark-mode! [!state]
  (let [{:keys [dark-mode?]} @!state]
    (add-watch !state ::dark-mode
               (fn [_ _ old {:keys [dark-mode?]}]
                 (when (not= (:dark-mode? old) dark-mode?)
                   (set-dark-mode! dark-mode?))))
    (when dark-mode?
      (set-dark-mode! dark-mode?))))

(defn notebook [{:as _doc xs :blocks :keys [toc]}]
  (r/with-let [local-storage-key "clerk-navbar"
               !state (r/atom {:toc (toc-items (:children toc))
                               :md-toc toc
                               :dark-mode? (ls/get-item local-storage-dark-mode-key)
                               :theme {:slide-over "bg-slate-100 dark:bg-gray-800 font-sans border-r dark:border-slate-900"}
                               :width 220
                               :mobile-width 300
                               :local-storage-key local-storage-key
                               :open? (if-some [stored-open? (ls/get-item local-storage-key)]
                                        stored-open?
                                        (not= :collapsed (:mode toc)))})
               root-ref-fn #(when % (setup-dark-mode! !state))
               ref-fn #(when % (swap! !state assoc :scroll-el %))]
    (let [{:keys [md-toc]} @!state]
      (when-not (= md-toc toc)
        (swap! !state assoc :toc (toc-items (:children toc)) :md-toc toc :open? (not= :collapsed (:mode toc))))
      (html
       [:div.flex
        {:ref root-ref-fn}
        [:div.fixed.top-2.left-2.md:left-auto.md:right-2.z-10
         [dark-mode-toggle !state]]
        (when (and toc (:mode toc))
          [:<>
           [navbar/toggle-button !state
            [:<>
             [icon/menu {:size 20}]
             [:span.uppercase.tracking-wider.ml-1.font-bold
              {:class "text-[12px]"} "ToC"]]
            {:class "z-10 fixed right-2 top-2 md:right-auto md:left-3 md:top-3 text-slate-400 font-sans text-xs hover:underline cursor-pointer flex items-center bg-white dark:bg-gray-900 py-1 px-3 md:p-0 rounded-full md:rounded-none border md:border-0 border-slate-200 dark:border-gray-500 shadow md:shadow-none dark:text-slate-400 dark:hover:text-white"}]
           [navbar/panel !state [navbar/navbar !state]]])
        [:div.flex-auto.h-screen.overflow-y-auto
         {:ref ref-fn}
         (into [:div.flex.flex-col.items-center.viewer-notebook.flex-auto]
               (map (fn [[_inspect x :as y]]
                      (let [{viewer-name :name} (viewer/->viewer x)
                            inner-viewer-name (some-> x viewer/->value viewer/->viewer :name)]
                        [:div {:class ["viewer" "overflow-x-auto" "overflow-y-hidden"
                                       (when viewer-name (str "viewer-" (name viewer-name)))
                                       (when inner-viewer-name (str "viewer-" (name inner-viewer-name)))
                                       (case (or (viewer/width x) (case viewer-name (:code :code-folded) :wide :prose))
                                         :wide "w-full max-w-wide"
                                         :full "w-full"
                                         "w-full max-w-prose px-8")]}
                         y])))
               xs)]]))))

(defn eval-viewer-fn [eval-f form]
  (try (eval-f form)
       (catch js/Error e
         (throw (ex-info (str "error in render-fn: " (.-message e)) {:render-fn form} e)))))

(defonce !edamame-opts
  (atom {:all true
         :row-key :line
         :col-key :column
         :location? seq?
         :end-location false
         :read-cond :allow
         :readers
         (fn [tag]
           (or (get {'viewer-fn   (partial eval-viewer-fn viewer/->viewer-fn)
                     'viewer-eval (partial eval-viewer-fn *eval*)} tag)
               (fn [value]
                 (with-viewer :tagged-value
                   {:tag tag
                    :space? (not (vector? value))
                    :value (cond-> value
                             (and (vector? value) (number? (second value)))
                             (update 1 (fn [memory-address]
                                         (with-viewer :number-hex memory-address))))}))))
         :features #{:clj}}))


(defn ^:export read-string [s]
  (edamame/parse-string s @!edamame-opts))

(defn opts->query [opts]
  (->> opts
       (map #(update % 0 name))
       (map (partial str/join "="))
       (str/join "&")))



#_(opts->query {:s 12 :num 42})

(defn unreadable-edn-viewer [edn]
  (html [:span.inspected-value.whitespace-nowrap.cmt-default edn]))

(defn error-badge [& content]
  [:div.bg-red-50.rounded-sm.text-xs.text-red-400.px-2.py-1.items-center.sans-serif.inline-flex
   [:svg.h-4.w-4.text-red-400 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :aria-hidden "true"}
    [:path {:fill-rule "evenodd" :d "M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" :clip-rule "evenodd"}]]
   (into [:div.ml-2.font-bold] content)])

(defn error-viewer [error]
  (html
   [:div.bg-red-100.dark:bg-gray-800.px-6.py-4.rounded-md.text-xs.dark:border-2.dark:border-red-300.not-prose
    [:p.font-mono.text-red-600.dark:text-red-300.font-bold (.-message error)]
    [:pre.text-red-600.dark:text-red-300.w-full.overflow-auto.mt-2
     {:class "text-[11px] max-h-[155px]"}
     (try
       (->> (.-stack error)
            str/split-lines
            (drop 1)
            (mapv str/trim)
            (str/join "\n"))
       (catch js/Error e
         nil))]
    (when-let [data (.-data error)]
      [:div.mt-2 [inspect-paginated data]])]))

(defn error-boundary [!error & _]
  (r/create-class
   {:constructor (fn [_ _])
    :component-did-catch (fn [_ e _info]
                           (reset! !error e))
    :get-derived-state-from-error (fn [e]
                                    (reset! !error e)
                                    #js {})
    :reagent-render (fn [_error & children]
                      (if-let [error @!error]
                        (viewer/->value (error-viewer error))
                        (into [:<>] children)))}))

(defn fetch! [{:keys [blob-id]} opts]
  #_(js/console.log :fetch! blob-id opts)
  (-> (js/fetch (str js/window.location.pathname
                     "/_blob/" blob-id
                     (when (seq opts)
                       (str "?" (opts->query opts)))))
      (.then #(.text %))
      (.then #(try (read-string %)
                   (catch js/Error e
                     (js/console.error #js {:message "sci read error" :blob-id blob-id :code-string % :error e })
                     (unreadable-edn-viewer %))))))

(defn read-result [{:nextjournal/keys [edn string]}]
  (if edn
    (try
      (read-string edn)
      (catch js/Error e
        (error-viewer e)))
    (unreadable-edn-viewer string)))

(defn result-viewer [{:as result :nextjournal/keys [fetch-opts hash]} _opts]
  (html (r/with-let [!hash (atom hash)
                     !error (atom nil)
                     !desc (r/atom (read-result result))
                     !fetch-opts (atom fetch-opts)
                     fetch-fn (when @!fetch-opts
                                (fn [opts]
                                  (.then (fetch! @!fetch-opts opts)
                                         (fn [more]
                                           (swap! !desc viewer/merge-presentations more opts)))))]
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
   "dark:bg-gray-900"
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
  "with a simple `inspect` and no `present` we don't move closing parens to children"
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

(defn elision-viewer [{:as fetch-opts :keys [total offset unbounded?]} _]
  (html [view-context/consume :fetch-fn
         (fn [fetch-fn]
           [:span.sans-serif.relative.whitespace-nowrap
            {:style {:border-radius 2 :padding (when (fn? fetch-fn) "1px 3px") :font-size 11 :top -1}
             :class (if (fn? fetch-fn)
                      "cursor-pointer bg-indigo-200 hover:bg-indigo-300 dark:bg-gray-700 dark:hover:bg-slate-600 text-gray-900 dark:text-white"
                      "text-gray-400 dark:text-slate-300")
             :on-click #(when (fn? fetch-fn)
                          (fetch-fn fetch-opts))} (- total offset) (when unbounded? "+") (if (fn? fetch-fn) " more…" " more elided")])]))

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
         (viewer/->value (string-viewer s opts)) "\""]))

(defn number-viewer [num]
  (html [:span.cmt-number.inspected-value
         (if (js/Number.isNaN num) "NaN" (str num))]))

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
   [:div.bg-red-100.dark:bg-gray-800.px-6.py-4.rounded-md.text-xs.dark:border-2.dark:border-red-400.not-prose
    [:h4.mt-0.uppercase.text-xs.dark:text-red-400.tracking-wide "Table Error"]
    [:p.mt-4.font-medium "Clerk’s table viewer does not recognize the format of your data:"]
    [:div.mt-2.flex
     [:div.text-red-500.mr-2 x-icon]
     [inspect data]]
    [:p.mt-4.font-medium "Currently, the following formats are supported:"]
    [:div.mt-2.flex.items-center
     [:div.text-green-500.mr-2 check-icon]
     [inspect-paginated {:column-1 [1 2]
                         :column-2 [3 4]}]]
    [:div.mt-2.flex.items-center
     [:div.text-green-500.mr-2 check-icon]
     [inspect-paginated [{:column-1 1 :column-2 3} {:column-1 2 :column-2 4}]]]
    [:div.mt-2.flex.items-center
     [:div.text-green-500.mr-2 check-icon]
     [inspect-paginated [[1 3] [2 4]]]]
    [:div.mt-2.flex.items-center
     [:div.text-green-500.mr-2 check-icon]
     [inspect-paginated {:head [:column-1 :column-2]
                         :rows [[1 3] [2 4]]}]]]))


(defn throwable-viewer [{:keys [via trace]}]
  (html
   [:div.w-screen.h-screen.overflow-y-auto.bg-gray-100.p-6.text-xs.monospace.flex.flex-col.not-prose
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

(defn tagged-value
  ([tag value] ({:space? true} tagged-value tag value))
  ([{:keys [space?]} tag value]
   [:span.inspected-value.whitespace-nowrap
    [:span.cmt-meta tag] (when space? nbsp) value]))

(defn normalize-viewer-meta [x]
  (if-let [viewer (-> x meta :nextjournal/viewer)]
    (with-viewer ({:html html-viewer
                   :reagent reagent-viewer} viewer viewer) x)
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

(defn render-with-viewer [opts viewer value]
  #_(js/console.log :render-with-viewer {:value value :viewer viewer :opts opts})
  (cond (or (fn? viewer) (viewer/viewer-fn? viewer))
        (viewer value opts)

        (and (map? viewer) (:render-fn viewer))
        (render-with-viewer opts (:render-fn viewer) value)

        #_#_ ;; TODO: maybe bring this back
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
  ([opts x]
   (let [value (viewer/->value x)]
     #_(prn :inspect value :valid-element? (react/isValidElement value) :viewer (viewer/->viewer x))
     (or (when (react/isValidElement value) value)
         (when-let [viewer (viewer/->viewer x)]
           (inspect opts (render-with-viewer (merge opts {:viewer viewer} (:nextjournal/opts x)) viewer value)))
         (throw (ex-info "inspect needs to be called on presented value" {:x x}))))))

(defn in-process-fetch [value opts]
  (.resolve js/Promise (viewer/present value opts)))

(defn inspect-paginated [value]
  (r/with-let [!state (r/atom nil)]
    (when (not= (:value @!state) value)
      (swap! !state assoc :value value :desc (viewer/present value)))
    [view-context/provide {:fetch-fn (fn [fetch-opts]
                                       (.then (in-process-fetch value fetch-opts)
                                              (fn [more]
                                                (swap! !state update :desc viewer/merge-presentations more))))}
     [inspect (:desc @!state)]]))

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
  [inspect-paginated (md "### Hello Markdown\n\n* a bullet point")])

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
  (when (contains? state :doc)
    (reset! !doc doc))
  (reset! !error error)
  (when-some [title (-> doc viewer/->value :title)]
    (set! (.-title js/document) title)))

(dc/defcard eval-viewer
  "Viewers that are lists are evaluated using sci."
  [inspect (with-viewer (viewer/->viewer-fn '(fn [x] (v/html [:h3 "Ohai, " x "! 👋"]))) "Hans")])

(dc/defcard notebook
  "Shows how to display a notebook document"
  [doc]
  [inspect-paginated (with-viewer :clerk/notebook @doc)]
  {::dc/class "p-0"
   ::dc/state
   {:blocks
    [(with-viewer :markdown "# Hello Markdown\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum velit nulla, sodales eu lorem ut, tincidunt consectetur diam. Donec in scelerisque risus. Suspendisse potenti. Nunc non hendrerit odio, at malesuada erat. Aenean rutrum quam sed velit mollis imperdiet. Sed lacinia quam eget tempor tempus. Mauris et leo ac odio condimentum facilisis eu sed nibh. Morbi sed est sit amet risus blandit ullam corper. Pellentesque nisi metus, feugiat sed velit ut, dignissim finibus urna.")
     (code "(shuffle (range 10))")
     (with-viewer :clerk/code-block {:text "(+ 1 2 3)"})
     (md "# And some more\n And some more [markdown](https://daringfireball.net/projects/markdown/).")
     (code "(shuffle (range 10))")
     (md "## Some math \n This is a formula.")
     (tex "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")
     (plotly {:data [{:y (shuffle (range 10)) :name "The Federation"}
                     {:y (shuffle (range 10)) :name "The Empire"}]})]}})

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
  [inspect-paginated (with-viewer :table @state)]
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

(defn katex-viewer [tex-string {:keys [inline?]}]
  (html (katex/to-html-string tex-string (j/obj :displayMode (not inline?)))))

(defn html-render [markup]
  (r/as-element
   (if (string? markup)
     [:span {:dangerouslySetInnerHTML {:__html markup}}]
     markup)))

(def html-viewer
  {:render-fn html-render})

(def html
  (partial with-viewer html-viewer))

(defn reagent-viewer [x]
  (r/as-element (cond-> x (fn? x) vector)))

(def mathjax-viewer (comp normalize-viewer-meta mathjax/viewer))
(def code-viewer (comp normalize-viewer-meta code/viewer))
(def plotly-viewer (comp normalize-viewer-meta plotly/viewer))
(def vega-lite-viewer (comp normalize-viewer-meta vega-lite/viewer))

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
    (str
      js/window.location.pathname
      "/_blob/" blob-id (when-let [opts (seq (dissoc src :blob-id))]
                          (str "?" (opts->query opts))))))

(def ^{:doc "Stub implementation to be replaced during static site generation. Clerk is only serving one page currently."}
  doc-url
  (sci/new-var 'doc-url (fn [x] (str "#" x))))

(def sci-viewer-namespace
  {'html html-render
   'inspect inspect
   'inspect-paginated inspect-paginated
   'result-viewer result-viewer
   'coll-viewer coll-viewer
   'map-viewer map-viewer
   'elision-viewer elision-viewer
   'tagged-value tagged-value
   'inspect-children inspect-children
   'set-viewers! set-viewers!
   'string-viewer string-viewer
   'quoted-string-viewer quoted-string-viewer
   'number-viewer number-viewer
   'table-error table-error
   'with-viewer with-viewer
   'with-viewers with-viewers
   'with-d3-require d3-require/with
   'clerk-eval clerk-eval
   'consume-view-context view-context/consume

   'throwable-viewer throwable-viewer
   'notebook-viewer notebook
   'katex-viewer katex-viewer
   'mathjax-viewer mathjax-viewer
   'code-viewer code-viewer
   'foldable-code-viewer foldable-code-viewer
   'plotly-viewer plotly-viewer
   'vega-lite-viewer vega-lite-viewer
   'reagent-viewer reagent-viewer
   'unreadable-edn-viewer unreadable-edn-viewer

   'doc-url doc-url
   'url-for url-for
   'read-string read-string})

(defonce !sci-ctx
  (atom (sci/init {:async? true
                   :disable-arity-checks true
                   :classes {'js goog/global
                             'framer-motion framer-motion
                             :allow :all}
                   :aliases {'j 'applied-science.js-interop
                             'reagent 'reagent.core
                             'v 'nextjournal.clerk.sci-viewer}
                   :namespaces (merge {'nextjournal.clerk.sci-viewer sci-viewer-namespace}
                                      sci.configs.js-interop/namespaces
                                      sci.configs.reagent/namespaces)})))

(defn eval-form [f]
  (sci/eval-form @!sci-ctx f))

(set! *eval* eval-form)

(swap! viewer/!viewers (fn [viewers]
                         (-> (into {} (map (juxt key (comp #(into [] (map viewer/process-render-fn) %)  val))) viewers)
                             (update :root concat js-viewers))))
