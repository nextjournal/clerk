(ns nextjournal.clerk.sci-viewer
  (:refer-clojure :exclude [meta with-meta vary-meta])
  (:require [applied-science.js-interop :as j]
            [cljs.reader]
            [clojure.core :as core]
            [goog.object]
            [goog.string :as gstring]
            [nextjournal.devcards :as dc]
            [nextjournal.devcards.main :as devcards-main]
            [nextjournal.viewer.code :as code]
            [nextjournal.viewer.katex :as katex]
            [nextjournal.viewer.markdown :as markdown]
            [nextjournal.viewer.mathjax :as mathjax]
            [nextjournal.viewer.plotly :as plotly]
            [nextjournal.viewer.vega-lite :as vega-lite]
            [nextjournal.view.context :as context]
            [react :as react]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [reagent.dom :as rdom]
            [re-frame.context :as rf]
            [clojure.string :as str]
            [sci.core :as sci]
            [sci.impl.vars]))

(defn color-classes [selected?]
  {:value-color (if selected? "white-90" "dark-green")
   :symbol-color (if selected? "white-90" "dark-blue")
   :prefix-color (if selected? "white-50" "black-30")
   :label-color (if selected? "white-90" "black-60")
   :badge-background-color (if selected? "bg-white-20" "bg-black-10")})


(declare inspect)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Custom metadata handling - supporting any cljs value - not compatible with core meta

(defn meta? [x] (contains? x :nextjournal/value))

(defn meta [data]
  (if (meta? data)
    data
    (assoc (core/meta data)
      :nextjournal/value (cond-> data
                           ;; IMeta is a protocol in cljs
                           (satisfies? IWithMeta data) (core/with-meta {})))))

(defn with-meta [data m]
  (cond (meta? data) (assoc m :nextjournal/value (:nextjournal/value data))
        (satisfies? IWithMeta data) (core/with-meta data m)
        :else
        (assoc m :nextjournal/value data)))

(defn vary-meta [data f & args]
  (with-meta data (apply f (meta data) args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Viewers (built on metadata)

(defn with-viewer
  "The given viewer will be used to display data"
  [data viewer]
  (vary-meta data assoc :nextjournal/viewer viewer))

(defn with-viewers
  "Binds viewers to types, eg {:boolean view-fn}"
  [data viewers]
  (vary-meta data assoc :nextjournal/viewers viewers))

(defn view-as
  "Like `with-viewer` but takes viewer as 1st argument"
  [viewer data]
  (with-viewer data viewer))

(defn html [v]
  (with-viewer v (if (string? v) :html :hiccup)))

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

(defn object-viewer [x {:as opts :keys [expanded-at path]}]
  (let [x' (obj->clj x)
        expanded? (some-> expanded-at deref (get path))]
    (html [:span.inspected-value "#js {"
           (into [:<>]
                 (comp (map-indexed (fn [idx k]
                                      [:<>
                                       [inspect k (update opts :path conj idx)]
                                       nbsp
                                       [inspect (value-of x k) (update opts :path conj idx)]]))
                       (interpose (if expanded? [:<> [:br] (repeat (inc (count path)) nbsp)] nbsp)))
                 (keys x')) "}"])))


(declare notebook)
(declare var)
(declare blob)

(defn toggle-expanded [expanded-at path event]
  (.preventDefault event)
  (.stopPropagation event)
  (swap! expanded-at update path not))

(defn coll-viewer [{:keys [open close]} xs {:as opts :keys [expanded-at path] :or {path []}}]
  (let [expanded? (some-> expanded-at deref (get path))]
    (html [:span.inspected-value
           {:class (when expanded? "inline-flex")}
           [:span
            [:span.hover:bg-indigo-50.bg-opacity-70.cursor-pointer.rounded-sm
             {:on-click (partial toggle-expanded expanded-at path)}
             open]
            (into [:<>]
                  (comp (map-indexed (fn [idx x] [inspect x (update opts :path conj idx)]))
                        (interpose (if expanded? [:<> [:br] nbsp] nbsp)))
                  xs)
            close]])))

(defn map-viewer [xs {:as opts :keys [expanded-at path] :or {path []}}]
  (let [expanded? (some-> expanded-at deref (get path))]
    (html [:span.inspected-value
           {:on-click (partial toggle-expanded expanded-at path)}
           [:span.hover:bg-indigo-50.bg-opacity-70.cursor-pointer.rounded-sm
            {:on-click (partial toggle-expanded expanded-at path)}
            "{"]
           (into [:<>]
                 (comp (map-indexed (fn [idx [k v]]
                                      [:<>
                                       [inspect k (update opts :path conj idx)]
                                       nbsp
                                       [inspect v (update opts :path conj idx)]]))
                       (interpose (if expanded? [:<> [:br] (repeat (inc (count path)) nbsp)] nbsp)))
                 xs)
           "}"])))

(defn tagged-value [tag value]
  [:span.inspected-value
   [:span.syntax-tag tag]
   (gstring/unescapeEntities "&nbsp;")
   value])

(def default-viewers
  [{:pred string? :fn #(html [:span.syntax-string.inspected-value "\"" % "\""])}
   {:pred number? :fn #(html [:span.syntax-number.inspected-value
                              (if (js/Number.isNaN %) "NaN" (str %))])}
   {:pred symbol? :fn #(html [:span.syntax-symbol.inspected-value %])}
   {:pred keyword? :fn #(html [:span.syntax-keyword.inspected-value (str %)])}
   {:pred nil? :fn #(html [:span.syntax-nil.inspected-value "nil"])}
   {:pred boolean? :fn #(html [:span.syntax-bool.inspected-value (str %)])}
   {:pred fn? :fn #(html [:span.inspected-value [:span.syntax-tag "ƒ"] "()"])}
   {:pred vector? :fn (partial coll-viewer {:open "[" :close "]"}) :fetch-opts {:n 20}}
   {:pred list? :fn (partial coll-viewer {:open "(" :close ")"})  :fetch-opts {:n 20}}
   {:pred set? :fn (partial coll-viewer {:open "#{" :close "}"}) :fetch-opts {:n 20}}
   {:pred map? :fn map-viewer :fetch-opts {:n 20}}
   {:pred array? :fn (partial coll-viewer {:open [:<> [:span.syntax-tag "#js "] "["] :close "]"})}
   {:pred uuid? :fn #(html (tagged-value "#uuid" [inspect (str %)]))}
   {:pred inst? :fn #(html (tagged-value "#inst" [inspect (str %)]))}
   ;; TODO {:pred #(implements? IDeref %) :fn #(html (tagged-value "#atom" (inspect [%])))}
   {:pred goog/isObject :fn object-viewer}

   ;; named viewers
   {:name :latex :pred string? :fn #(html (katex/to-html-string %))}
   {:name :mathjax :pred string? :fn mathjax/viewer}
   {:name :html :pred string? :fn #(html [:div {:dangerouslySetInnerHTML {:__html %}}])}
   {:name :hiccup :fn #(r/as-element %)}
   {:name :plotly :pred map? :fn plotly/viewer}
   {:name :vega-lite :pred map? :fn vega-lite/viewer}
   {:name :markdown :pred string? :fn markdown/viewer}
   {:name :code :pred string? :fn code/viewer}
   {:name :reagent :fn #(r/as-element (cond-> % (fn? %) vector))}

   {:name :clerk/notebook :fn notebook}
   {:name :clerk/var :fn var}
   {:name :clerk/blob :fn blob}])


(def ^:dynamic *eval-form* nil)

(defn error-badge [& content]
  [:div.bg-red-50.rounded-sm.text-xs.text-red-400.px-2.py-1.items-center.sans-serif.inline-flex
   [:svg.h-4.w-4.text-red-400 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :aria-hidden "true"}
    [:path {:fill-rule "evenodd" :d "M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" :clip-rule "evenodd"}]]
   (into [:div.ml-2.font-bold] content)])

(def default-inspect-opts
  {:viewers default-viewers :expanded-at (r/atom {}) :path []})

(defn map-inspect-xf [opts]
  (map (fn [x] [inspect x opts])))

(defn inspect
  ([x] (inspect x default-inspect-opts))
  ([x {:as opts :keys [viewers]}]
   (if-let [selected-viewer (-> x meta :nextjournal/viewer)]
     (let [x (:nextjournal/value x x)]
       (cond (keyword? selected-viewer)
             (if-let [fn (get (into {} (map (juxt :name :fn)) viewers) selected-viewer)]
               [fn x opts]
               [error-badge "cannot find viewer named " (str selected-viewer)])
             (fn? selected-viewer)
             [selected-viewer x opts]
             (list? selected-viewer)
             (let [fn (*eval-form* selected-viewer)]
               [fn x opts])))
     (loop [v viewers]
       (if-let [{:keys [pred fn]} (first v)]
         (if (and pred fn (pred x))
           [fn x opts]
           (recur (rest v)))
         [error-badge "no matching viewer"])))))

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
                     #js {:js "object"}
                     #js ["a" "js" "array"]
                     (js/Date.)
                     (random-uuid)
                     (fn a-function [_foo])
                     (atom "an atom")
                     #_#_#_
                     ^{:nextjournal/tag 'object} ['clojure.lang.Atom 0x2c42b421 {:status :ready, :val 1}]
                     ^{:nextjournal/tag 'var} ['user/a {:foo :bar}]
                     ^{:nextjournal/tag 'object} ['clojure.lang.Ref 0x73aff8f1 {:status :ready, :val 1}]]]
          [:div.mb-3.result-viewer
           [:pre [:code.inspected-value (binding [*print-meta* true] (pr-str value))]] [:span.inspected-value " => "]
           [inspect value]])))

(declare inspect)




(comment
  :nav/path node-id ;; a list of keys we have navigated down to
  :nav/value node-id nav-path;; the object for a node-id and a nav-path (see above) ,
  )

;; (dc/defcard viewer-overlays
;;   "Shows how to override how values are being displayed."
;;   [state]
;;   [:div.result-data
;;    [inspect (with-viewers @state
;;               {:number #(str/join (take % (repeat "*")))
;;                :boolean #(view-as :hiccup
;;                                   [:div.inline-block {:stle {:width 12 :height 12}
;;                                                       :class (if % "bg-red" "bg-green")}])})]]
;;   {::dc/state {:a 1
;;                :b 2
;;                :c 3
;;                :d true
;;                :e false}})


(dc/when-enabled
 (def rule-30-state
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
     (->> g1 (iterate evolve) (take 17)))))

#_
(dc/defcard rule-30-types
  "Rule 30 using viewers based on types. Also shows how to use a named viewer for a number."
  [state]
  [:div.result-data
   [inspect (with-viewers
              @state
              {:number :cell ;; use the cell viewer for numbers
               :cell #(view-as :hiccup
                               [:div.inline-block {:class (if (zero? %)
                                                            "bg-white border-solid border-2 border-black"
                                                            "bg-black")
                                                   :style {:width 16 :height 16}}])
               :vector (fn [x options]
                         (->> x
                              (map (fn [x] [inspect x options]))
                              (into [:div.flex.inline-flex])
                              (view-as :hiccup)))
               :list (fn [x options]
                       (->> x
                            (map (fn [x] [inspect x options]))
                            (into [:div.flex.flex-col])
                            (view-as :hiccup)))})]]
  {::dc/state rule-30-state})

#_
(dc/defcard rule-30-child-options
  "Rule 30 using viewers based on viewer options (without overriding global types) and passing the viewer option down to child components."
  [state]
  [:div.result-data
   [inspect (-> @state
                (with-viewer :board)
                (with-viewers
                  {:cell #(view-as :hiccup
                                   [:div.inline-block {:class (if (zero? %)
                                                                "bg-white border-solid border-2 border-black"
                                                                "bg-black")
                                                       :style {:width 16 :height 16}}])
                   :row (fn [x options]
                          (->> x
                               (map #(inspect options (view-as :cell %)))
                               (into [:div.flex.inline-flex])
                               (view-as :hiccup)))
                   :board (fn [x options]
                            (->> x
                                 (map #(inspect options (view-as :row %)))
                                 (into [:div.flex.flex-col])
                                 (view-as :hiccup)))}))]]
  {::dc/state rule-30-state})

(dc/defcard rule-30-html
  "Rule 30 using viewers based on a single viewer rendering the board."
  [state]
  [:div.result-data
   [inspect (with-viewer @state (fn [board]
                                  (let [cell #(vector :div.inline-block
                                                      {:class (if (zero? %)
                                                                "bg-white border-solid border-2 border-black"
                                                                "bg-black")
                                                       :style {:width 16 :height 16}})
                                        row #(into [:div.flex.inline-flex] (map cell) %)]
                                    (html (into [:div.flex.flex-col] (map row) board)))))]]
  {::dc/state rule-30-state})

#_#_#_#_#_#_#_#_#_#_#_#_#_
(dc/defcard rule-30-sci-eval
  "Rule 30 using viewers based on sci eval."
  [inspect (with-viewer '([0 1 0] [1 0 1])
             '(fn [board]
                (let [cell #(vector :div.inline-block
                                    {:class (if (zero? %)
                                              "bg-white border-solid border-2 border-black"
                                              "bg-black")
                                     :style {:width 16 :height 16}})
                      row #(into [:div.flex.inline-flex] (map cell) %)]
                  (v/html (into [:div.flex.flex-col] (map row) board)))))])


(dc/defcard inspect-in-process
  "Different datastructures that live in-process in the browser. More values can just be displayed without needing to fetch more data."
  [:div
   [:div [inspect (range 1000)]]
   [:div [inspect (vec (range 1000))]]
   [:div [inspect (zipmap (range 1000) (range 1000))]]])

(dc/defcard inspect-large-values
  "Defcard for larger datastructures clj and json, we make use of the db viewer."
  [:div
   (let [gen-keyword #(keyword (gensym))
         generate-ds (fn [x val-fun]
                       (loop [x x res {}]
                         (cond
                           (= x 0) res
                           :else (recur (dec x) (assoc res (gen-keyword) (val-fun))))))
         value-1 (generate-ds 42 gen-keyword)]
     [inspect (generate-ds 42 (fn [] (clj->js value-1)))])])

(dc/defcard viewer-reagent-atom
  [inspect (r/atom {:hello :world})])

(dc/defcard viewer-js-window []
  [inspect js/window])

(dc/defcard viewer-vega-lite
  [inspect (view-as :vega-lite
                    {:width 650
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
  [inspect (view-as :plotly
                    {:data [{:y (shuffle (range 10)) :name "The Federation" }
                            {:y (shuffle (range 10)) :name "The Empire"}]})])

(dc/defcard viewer-latex
  [inspect (view-as :latex
                    "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")])

(dc/defcard viewer-mathjax
  [inspect (view-as :mathjax
                    "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")])

(dc/defcard viewer-markdown
  [inspect (view-as :markdown "### Hello Markdown\n\n- a bullet point")])

(dc/defcard viewer-code
  [inspect (view-as :code "(str (+ 1 2) \"some string\")")])

(dc/defcard viewer-hiccup
  [inspect (view-as :hiccup [:h1 "Hello Hiccup 👋"])])

(dc/defcard viewer-reagent-component
  "A simple counter component in reagent using `reagent.core/with-let`."
  [inspect (view-as :reagent
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
   [inspect (with-viewer 0.33
              #(html
                [:div.relative.pt-1
                 [:div.overflow-hidden.h-2.mb-4-text-xs.flex.rounded.bg-teal-200
                  [:div.shadow-none.flex.flex-col.text-center.whitespace-nowrap.text-white.bg-teal-500
                   {:style {:width (-> %
                                       (* 100)
                                       int
                                       (max 0)
                                       (min 100)
                                       (str "%"))}}]]]))]
   [inspect (with-viewer 0.35
              (fn [v _opts] (html
                             [:div.relative.pt-1
                              [:div.overflow-hidden.h-2.mb-4-text-xs.flex.rounded.bg-teal-200
                               [:div.shadow-none.flex.flex-col.text-center.whitespace-nowrap.text-white.bg-teal-500
                                {:style {:width (-> v
                                                    (* 100)
                                                    int
                                                    (max 0)
                                                    (min 100)
                                                    (str "%"))}}]]])))]])



(defn notebook [xs]
  (html
   [:div.notebook-viewer
    (into [:div.p-4.lg:px-0.md:py-8.md:max-w-2xl.mx-auto.flex.flex-col]
          (map #(html
                 (let [viewer (-> % meta :nextjournal/viewer)]
                   [:div {:class (case viewer
                                   :code "viewer viewer-code"
                                   (:markdown :latex) "viewer viewer-markdown"
                                   (:plotly :vega-lite) "viewer viewer-plot"
                                   "viewer viewer-data overflow-x-auto")}
                    (cond-> [inspect %] (:blob/id %) (vary-meta assoc :key (:blob/id %)))]))) xs)]))

(defn var [x]
  (html [:span.inspected-value
         [:span.syntax-tag "#'" (str x)]]))

(defonce state (ratom/atom nil))
(defn root []
  [inspect @state])

(def ^:export read-string
  (partial cljs.reader/read-string {:default identity}))

(defn ^:export mount [el]
  (rdom/render [root] el))

(defn ^:export reset-state [new-state]
  (reset! state new-state))

(defn opts->query [opts]
  (->> opts
       (map #(update % 0 name))
       (map (partial str/join "="))
       (str/join "&")))

#_(opts->query {:s 10 :num 42})


(defn describe [result]
  #_
  (cond-> {:nextjournal/type-key (value-type result) :blob/id (-> result meta :blob/id)}
    (counted? result)
    (assoc :count (count result))))

#_(describe (vec (range 100)))

(defn paginate [result {:as opts :keys [start n] :or {start 0}}]
  #_(log/info :paginate {:start start :n n :result result})
  (if (and (number? n)
           (pos? n)
           (not (or (map? result)
                    (set? result)))
           (counted? result))
    (with-meta (->> result (drop start) (take n) doall) (merge opts (describe result)))
    result))

#_(meta (paginate (vec (range 10)) {:n 20}))
#_(meta (paginate (vec (range 100)) {:n 20}))
#_(meta (paginate (zipmap (range 100) (range 100)) {:n 20}))
#_(paginate #{1 2 3} {:n 20})

(rf/reg-sub
 ::blobs
 (fn [db [blob-key id :as v]]
   (if id
     (get-in db v)
     (get db blob-key))))


(defn fetch! [!result {:blob/keys [id]} opts]
  #_(log/trace :fetch! opts)
  (-> (js/fetch (str "_blob/" id (when (seq opts)
                                   (str "?" (opts->query opts)))))
      (.then #(.text %))
      (.then #(reset! !result {:value (read-string %)}))
      (.catch #(reset! !result {:error %}))))

(defn in-process-fetch! [!result {:blob/keys [id]} opts]
  (-> (js/Promise. (fn [resolve _reject]
                     (resolve @(rf/subscribe [::blobs id]))))
      (.then #(paginate % opts))
      (.then #(reset! !result {:value (doto % #_(log/info :in-process-fetch!/value))}))
      (.catch #(reset! !result {:error %}))))

(defn get-fetch-opts [{:keys [nextjournal/type-key count]}]
  #_
  (cond
    (and (number? count)
         (pos? count)
         (contains? #{:list :vector} type-key)
         (not (@!viewers type-key))) {:n increase-items}))

#_(get-fetch-opts {})
#_(get-fetch-opts {:type-key :vector :count 1000})

(defn blob [blob]
  (r/with-let [!result (r/atom {:loading? true})
               fetch! (partial (:blob/fetch! blob fetch!) !result blob)
               _ (fetch! (get-fetch-opts blob))]
    (let [{:keys [value error loading?]} @!result]
      (cond value (view-as :reagent [inspect value]
                           ;; TODO
                           #_[context/provide {:fetch! fetch!}
                              [inspect value]])
            error (html [:span.red "error" (str error)])
            loading? (html "loading…")))))

(dc/defcard blob-in-process-fetch
  "Dev affordance that performs fetch in-process."
  (into [:div]
        (map (fn [[blob-id v]] [:div [inspect (view-as :clerk/blob (assoc (describe (with-meta v {:blob/id blob-id})) :blob/fetch! in-process-fetch!))]]))
        @(rf/subscribe [::blobs]))
  {:blobs (hash-map (random-uuid) (vec (drop 500 (range 1000)))
                    (random-uuid) (range 1000)
                    (random-uuid) (zipmap (range 1000) (range 1000)))})


(def sci-viewer-namespace
  {'html html
   'view-as view-as
   'inspect inspect
   #_#_#_#_
   'register-viewer! register-viewer!
   'register-viewers! register-viewers!
   'with-viewer with-viewer
   'with-viewers with-viewers})


(defonce ctx
  (sci/init {:async? true
             :disable-arity-checks true
             :classes {'js goog/global
                       :allow :all}
             :bindings {'atom ratom/atom}
             :namespaces {'nextjournal.viewer sci-viewer-namespace
                          'v sci-viewer-namespace}}))


(defn eval-form [f]
  (sci/eval-form ctx f))

(set! *eval-form* eval-form)


(dc/defcard eval-viewer
  "Viewers that are lists are evaluated using sci."
  [inspect (with-viewer "Hans" '(fn [x] (v/with-viewer [:h3 "Ohai, " x "! 👋"] :hiccup)))])

#_
(dc/defcard notebook
  "Shows how to display a notebook document"
  [state]
  [inspect ^{:nextjournal/viewer :clerk/notebook} [(view-as :markdown "# Hello Markdown\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum velit nulla, sodales eu lorem ut, tincidunt consectetur diam. Donec in scelerisque risus. Suspendisse potenti. Nunc non hendrerit odio, at malesuada erat. Aenean rutrum quam sed velit mollis imperdiet. Sed lacinia quam eget tempor tempus. Mauris et leo ac odio condimentum facilisis eu sed nibh. Morbi sed est sit amet risus blandit ullam corper. Pellentesque nisi metus, feugiat sed velit ut, dignissim finibus urna.")
                                                   [1 2 3 4]
                                                   (view-as :code "(shuffle (range 10))")
                                                   {:hello [0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9]}
                                                   (view-as :markdown "# And some more\n And some more [markdown](https://daringfireball.net/projects/markdown/).")
                                                   (view-as :code "(shuffle (range 10))")
                                                   (view-as :markdown "## Some math \n This is a formula.")
                                                   (view-as :latex
                                                            "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")
                                                   (view-as :plotly
                                                            {:data [{:y (shuffle (range 10)) :name "The Federation" }
                                                                    {:y (shuffle (range 10)) :name "The Empire"}]})]])

(defn ^:export ^:dev/after-load devcards []
  (devcards-main/init))

(def ^:dynamic *viewers* nil)

(dc/defcard inspect-rule-30-sci
  []
  [inspect
   '([0 1 0] [1 0 1])
   (assoc default-inspect-opts
          :viewers
          [{:pred number?
            :fn #(html [:div.inline-block {:style {:width 16 :height 16}
                                           :class (if (pos? %) "bg-black" "bg-white border-solid border-2 border-
black")}])}
           {:pred vector? :fn #(html (into [:div.flex.inline-flex] (map-inspect-xf %2) %1))}
           {:pred list? :fn #(html (into [:div.flex.flex-col] (map-inspect-xf %2) %1))}])])

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
     #_[:div.mb-4
        [map-viewer '{1 ● 2 ■ 3 ▲}]]
     #_[:div.mb-4
        [inspect {[[[[1 2]]]] [1 2]}]]

     [:div
      {:style {:margin-right -12}}
      [:div.mb-4.overflow-x-hidden
       [inspect x]]
      [:div.mb-4.overflow-x-hidden
       [inspect x {:viewers default-viewers
                   :path []
                   :expanded-at (r/atom {[] true
                                         [0] true
                                         [1] true
                                         [0 0] true})}]]
      #_#_[:div.mb-4.overflow-x-hidden
           [inspect x]]
      [:div.mb-4.overflow-x-hidden
       [inspect y]]]]))
