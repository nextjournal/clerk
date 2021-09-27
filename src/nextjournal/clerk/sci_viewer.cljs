(ns nextjournal.clerk.sci-viewer
  (:require [applied-science.js-interop :as j]
            [cljs.reader]
            [clojure.string :as str]
            [goog.object]
            [goog.string :as gstring]
            [nextjournal.clerk.viewer :as viewer :refer [html with-viewer with-viewers view-as]]
            [nextjournal.devcards :as dc]
            [nextjournal.devcards.main]
            [nextjournal.viewer.code :as code]
            [nextjournal.viewer.katex :as katex]
            [nextjournal.viewer.markdown :as markdown]
            [nextjournal.viewer.mathjax :as mathjax]
            [nextjournal.viewer.plotly :as plotly]
            [nextjournal.viewer.vega-lite :as vega-lite]
            [re-frame.context :as rf]
            [react :as react]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [reagent.dom :as rdom]
            [sci.core :as sci]
            [sci.impl.namespaces]
            [sci.impl.vars]))

(defn color-classes [selected?]
  {:value-color (if selected? "white-90" "dark-green")
   :symbol-color (if selected? "white-90" "dark-blue")
   :prefix-color (if selected? "white-50" "black-30")
   :label-color (if selected? "white-90" "black-60")
   :badge-background-color (if selected? "bg-white-20" "bg-black-10")})


(declare inspect)
(declare inspect-lazy)

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

(defn object-viewer [x {:as opts :keys [!expanded-at path]}]
  (let [x' (obj->clj x)
        expanded? (some-> !expanded-at deref (get path))]
    (html [:span.inspected-value.whitespace-nowrap "#js {"
           (into [:<>]
                 (comp (map-indexed (fn [idx k]
                                      [:<>
                                       [inspect k (update opts :path conj idx)]
                                       " "
                                       [inspect (value-of x k) (update opts :path conj idx)]]))
                       (interpose (if expanded? [:<> [:br] (repeat (inc (count path)) " ")] " ")))
                 (keys x')) "}"])))



(defn notebook [xs]
  (html
   (into [:div.flex.flex-col.items-center.viewer-notebook]
         (map (fn [x]
                (let [viewer (viewer/viewer x)
                      width (:nextjournal/width x)
                      blob-id (:blob-id (viewer/value x))]
                  [:div {:class ["viewer"
                                 (when (keyword? viewer)
                                   (str "viewer-" (name viewer)))
                                 (case (or width (case viewer
                                                   :code :wide
                                                   :prose))
                                   :wide "w-full max-w-wide px-8"
                                   :full "w-full"
                                   "w-full max-w-prose px-8 overflow-x-auto")]}
                   (cond-> [inspect x]
                     blob-id (with-meta {:key blob-id}))])))
         xs)))

(defn var [x]
  (html [:span.inspected-value
         [:span.syntax-tag "#'" (str x)]]))

(def ^:export read-string
  (partial cljs.reader/read-string {:default identity}))

(defn opts->query [opts]
  (->> opts
       (map #(update % 0 name))
       (map (partial str/join "="))
       (str/join "&")))

#_(opts->query {:s 10 :num 42})


(defn fetch! [{:keys [blob-id]} opts]
  #_(js/console.log :fetch blob-id opts)
  (-> (js/fetch (str "_blob/" blob-id (when (seq opts)
                                        (str "?" (opts->query opts)))))
      (.then #(.text %))
      (.then #(read-string %))))

(defn in-process-fetch [xs opts]
  (.resolve js/Promise (viewer/fetch xs opts)))

(defn result [desc opts]
  (html [inspect-lazy (-> desc
                          (assoc :fetch-fn (partial fetch! desc))) opts]))

(defn toggle-expanded [!expanded-at path event]
  (.preventDefault event)
  (.stopPropagation event)
  (swap! !expanded-at update path not))

(defn concat-into [xs ys]
  (if (or (vector? xs) (set? xs) (map? xs))
    (into xs ys)
    (concat xs ys)))

(defn more-button [count {:keys [!x path desc path->info]}]
  (when-some [more (let [more (- (get-in path->info [path :count]) count)]
                     (when (pos? more) more))]
    (let [fetch-opts (-> desc :viewer :fetch-opts)
          {:keys [fetch-fn unbounded?]} desc]
      [:<> " "
       [:span.bg-gray-200.hover:bg-gray-200.cursor-pointer.sans-serif.relative.whitespace-nowrap
        {:style {:border-radius 2 :padding "1px 3px" :font-size 11 :top -1}
         :on-click (fn [_e] (.then (fetch-fn (assoc fetch-opts :offset count))
                                   #(swap! !x update path concat-into %)))} more (when unbounded? "+") " more…"]])))

(defn expanded-path? [!expanded-at path]
  (some-> !expanded-at deref (get path)))

(defn expandable-path? [!expanded-at path]
  (or
   (= path [])
   (expanded-path? !expanded-at (vec (drop-last path)))))

(defn coll-viewer [{:keys [open close]} xs {:as opts :keys [!expanded-at path] :or {path []}}]
  (let [expanded? (expanded-path? !expanded-at path)
        expandable? (expandable-path? !expanded-at path)]
    (html [:span.inspected-value.whitespace-nowrap
           {:class (when expanded? "inline-flex")}
           [:span
            [:span.bg-opacity-70.rounded-sm.whitespace-nowrap
             (when expandable?
               {:on-click (partial toggle-expanded !expanded-at path)
                :class ["cursor-pointer" "hover:bg-indigo-50"]})
             open]
            (into [:<>]
                  (comp (map-indexed (fn [idx x] [inspect x (update opts :path conj idx)]))
                        (interpose (if expanded? [:<> [:br] nbsp] " ")))
                  xs)
            (more-button (count xs) opts)
            close]])))

(def elision-pred
  (partial = :nextjournal/…))

(defn elision-viewer [_ {:as opts :keys [!x path desc] :or {path []}}]
  (let [fetch-opts (-> desc :viewer :fetch-opts)
        {:keys [fetch-fn]} desc]
    (html [:span.bg-gray-200.hover:bg-gray-200.cursor-pointer.sans-serif.relative
           {:style {:border-radius 2 :padding "1px 3px" :font-size 11 :top -1}
            :on-click (fn [_e] (.then (fetch-fn (assoc fetch-opts :path path))
                                      (fn [x] (swap! !x assoc path x))))} "…"])))

(defn map-viewer [xs {:as opts :keys [!expanded-at path] :or {path []}}]
  (let [expanded? (expanded-path? !expanded-at path)
        expandable? (expandable-path? !expanded-at path)]
    (html [:span.inspected-value.whitespace-nowrap
           (when expandable?
             {:on-click (partial toggle-expanded !expanded-at path)})
           [:span.bg-opacity-70.rounded-sm
            (when expandable?
              {:on-click (partial toggle-expanded !expanded-at path)
               :class ["cursor-pointer" "hover:bg-indigo-50"]})
            "{"]
           (into [:<>]
                 (comp (map-indexed (fn [idx [k v]]
                                      [:<>
                                       [inspect k (update opts :path conj idx)]
                                       " "
                                       [inspect v (update opts :path conj idx)]]))
                       (interpose (if expanded? [:<> [:br] (repeat (inc (count path)) nbsp)] " ")))
                 xs)
           (more-button (count xs) opts)
           "}"])))

(defn tagged-value [tag value]
  [:span.inspected-value.whitespace-nowrap
   [:span.syntax-tag tag]
   (gstring/unescapeEntities "&nbsp;")
   value])

(def ^:dynamic *eval-form*)

(defn normalize-viewer [x]
  (if-let [viewer (-> x meta :nextjournal/viewer)]
    (viewer/with-viewer x viewer)
    x))

(def named-viewers
  [;; named viewers
   {:name :latex :pred string? :fn #(html (katex/to-html-string %))}
   {:name :mathjax :pred string? :fn (comp normalize-viewer mathjax/viewer)}
   {:name :html :pred string? :fn #(html [:div {:dangerouslySetInnerHTML {:__html %}}])}
   {:name :hiccup :fn r/as-element}
   {:name :plotly :pred map? :fn (comp normalize-viewer plotly/viewer)}
   {:name :vega-lite :pred map? :fn (comp normalize-viewer vega-lite/viewer)}
   {:name :markdown :pred string? :fn markdown/viewer}
   {:name :code :pred string? :fn (comp normalize-viewer code/viewer)}
   {:name :reagent :fn #(r/as-element (cond-> % (fn? %) vector))}
   {:name :eval! :fn #(*eval-form* %)}

   {:name :clerk/notebook :fn notebook}
   {:name :clerk/var :fn var}
   {:name :clerk/result :fn result}])

(def js-viewers
  [{:pred #(implements? IDeref %) :fn #(tagged-value (-> %1 type pr-str) (inspect (deref %1) %2))}
   {:pred goog/isObject :fn object-viewer}
   {:pred array? :fn (partial coll-viewer {:open [:<> [:span.syntax-tag "#js "] "["] :close "]"})}])


(reset! viewer/!viewers
        {:root (into [] (concat viewer/default-viewers js-viewers named-viewers))})

(defonce !doc (ratom/atom nil))
(defonce !viewers viewer/!viewers)

(defn set-viewers! [scope viewers]
  #_(js/console.log :set-viewers! {:scope scope :viewers viewers})
  (swap! !viewers assoc scope (vec viewers))
  'set-viewers!)

(defn inspect-children [opts]
  (map-indexed (fn [idx x] [inspect x (update opts :path conj idx)])))

(def sci-viewer-namespace
  {'html html
   'view-as view-as
   'inspect inspect
   'coll-viewer coll-viewer
   'map-viewer map-viewer
   'tagged-value tagged-value
   'inspect-children inspect-children
   'set-viewers! set-viewers!
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

(defn error-badge [& content]
  [:div.bg-red-50.rounded-sm.text-xs.text-red-400.px-2.py-1.items-center.sans-serif.inline-flex
   [:svg.h-4.w-4.text-red-400 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :aria-hidden "true"}
    [:path {:fill-rule "evenodd" :d "M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" :clip-rule "evenodd"}]]
   (into [:div.ml-2.font-bold] content)])

(defn error-boundary [& children]
  (let [error (r/atom nil)]
    (r/create-class
     {:constructor (fn [this props])
      :component-did-catch (fn [this e info])
      :get-derived-state-from-error (fn [e]
                                      (reset! error e)
                                      #js {})
      :reagent-render (fn [& children]
                        (if @error
                          (error-badge "rendering error")
                          (into [:<>] children)))})))

(defn render-with-viewer [{:as opts :keys [viewers]} viewer x]
  #_(js/console.log :render-with-viewer #_opts viewer viewers x)
  (cond (keyword? viewer)
        (if-let [{render-fn :fn :keys [fetch-opts]} (get (into {} (map (juxt :name identity)) viewers) viewer)]
          (if-not render-fn
            (error-badge "no render function for viewer named " (str viewer))
            (render-fn x (assoc opts :fetch-opts fetch-opts)))
          (error-badge "cannot find viewer named " (str viewer)))
        (fn? viewer)
        (viewer x opts)
        (list? viewer)
        (let [render-fn (*eval-form* viewer)]
          #_(js/console.log :eval viewer :render-fn render-fn)
          (render-fn x opts))))

(defn inspect
  ([x]
   [inspect x {:path [] :!expanded-at (r/atom {}) :!x (r/atom {})}])
  ([x {:as opts :keys [!x viewers path path->info]}]
   (let [x (or (some-> !x deref (get path)) x)
         {:as opts :keys [viewers]} (assoc opts :viewers (vec (concat (viewer/viewers x) viewers)))
         all-viewers (viewer/get-viewers (:scope @!doc) viewers)
         selected-viewer (or (viewer/viewer x)
                             (when (elision-pred x) elision-viewer)
                             (get-in path->info [path :viewer :fn]))
         val (viewer/value x)]
     #_(js/console.log :val val :path path :viewer selected-viewer :type-val (type val) :react? (react/isValidElement val) :type (type selected-viewer)  :viewers-count (some-> viewers count) :viewers viewers :local-viewers (viewer/viewers x))
     (or (when (react/isValidElement val) val)
         (when selected-viewer
           (inspect (render-with-viewer (assoc opts :viewers all-viewers) selected-viewer val) (dissoc opts :path)))
         (loop [v all-viewers]
           #_(js/console.log :loop v )
           (if-let [{render-fn :fn :keys [pred]} (first v)]
             (let [render-fn (cond-> render-fn (not (fn? render-fn)) *eval-form*)
                   pred (cond-> pred (not (fn? pred)) *eval-form*)]
               #_(js/console.log :pred pred :match? (and pred render-fn (pred val)) :r (render-fn x opts))
               (if (and pred render-fn (pred val))
                 (viewer/value (render-fn x opts))
                 (recur (rest v))))
             (error-badge "no matching viewer")))))))

(defn inspect-lazy [{:as desc :keys [fetch-fn]} opts]
  (let [path->info (viewer/path->info desc)
        {:as opts :keys [!x]} (assoc (or opts {:path []}) :expanded-at (r/atom {}) :!x (r/atom {}) :desc desc :path->info path->info)]
    (r/create-class
     {:display-name "inspect-lazy"
      :component-did-mount
      (fn [_this]
        (doseq [[path {:keys [fetch-opts]}] path->info]
          (-> (fetch-fn fetch-opts)
              (.then (fn [x] (swap! !x assoc path x)))
              (.catch (fn [e] (js/console.error e))))))

      :reagent-render
      (fn render-inspect-lazy [_]
        (if (seq @!x)
          [error-boundary [inspect nil opts]]
          [:span "loading…"]))})))

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


#_ ;; commented out because it's slow since it renders all values, re-enable once we don't display all results
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
  ;; TODO
  [inspect (r/atom {:hello :world})])

#_ ;; commented out because recursive window prop will cause a loop
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



(defn root []
  [inspect @!doc])

(defn ^:export reset-doc [new-doc]
  (reset! !doc new-doc))

(rf/reg-sub
 ::blobs
 (fn [db [blob-key id]]
   (cond-> (get db blob-key)
     id (get id))))

(defn lazy-inspect-in-process [xs]
  [inspect-lazy (assoc (viewer/describe xs) :fetch-fn (partial in-process-fetch xs))])

(dc/defcard blob-in-process-fetch-single
  []
  [:div
   (when-let [xs @(rf/subscribe [::blobs :vector-nested-taco])]
     [lazy-inspect-in-process xs])]
  {::blobs {:vector (vec (range 30))
            :vector-nested [1 [2] 3]
            :vector-nested-taco '[l [l [l [l [🌮] r] r] r] r]
            :list (range 30)
            :map-1 {:hello :world}
            :map (zipmap (range 30) (range 30))}})

(dc/defcard blob-in-process-fetch
  "Dev affordance that performs fetch in-process."
  []
  [:div
   (map (fn [[blob-id xs]]
          ^{:key blob-id}
          [:div
           [lazy-inspect-in-process xs]])
        @(rf/subscribe [::blobs]))]
  {::blobs (hash-map (random-uuid) (vec (range 30))
                     (random-uuid) (range 40)
                     (random-uuid) (zipmap (range 50) (range 50)))})



(dc/defcard eval-viewer
  "Viewers that are lists are evaluated using sci."
  [inspect (with-viewer "Hans" '(fn [x] (v/with-viewer [:h3 "Ohai, " x "! 👋"] :hiccup)))])


(dc/defcard result
  [inspect (view-as :clerk/result
                    {:path [], :count 49, :blob-id "5dqpVeeZvAkHU546wCpFjr6GDHxkZh"})])

(dc/defcard notebook
  "Shows how to display a notebook document"
  [state]
  [inspect
   (view-as :clerk/notebook
            [(view-as :markdown "# Hello Markdown\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum velit nulla, sodales eu lorem ut, tincidunt consectetur diam. Donec in scelerisque risus. Suspendisse potenti. Nunc non hendrerit odio, at malesuada erat. Aenean rutrum quam sed velit mollis imperdiet. Sed lacinia quam eget tempor tempus. Mauris et leo ac odio condimentum facilisis eu sed nibh. Morbi sed est sit amet risus blandit ullam corper. Pellentesque nisi metus, feugiat sed velit ut, dignissim finibus urna.")
             [1 2 3 4]
             (view-as :code "(shuffle (range 10))")
             {:hello [0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9]}
             (view-as :markdown "# And some more\n And some more [markdown](https://daringfireball.net/projects/markdown/).")
             (view-as :code "(shuffle (range 10))")
             (view-as :markdown "## Some math \n This is a formula.")
             (view-as :latex
                      "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")
             (view-as :plotly
                      {:data [{:y (shuffle (range 10)) :name "The Federation"}
                              {:y (shuffle (range 10)) :name "The Empire"}]})])]
  {::dc/class "p-0"})


(def ^:dynamic *viewers* nil)

(dc/defcard inspect-rule-30-sci
  []
  [inspect
   '([0 1 0] [1 0 1])
   (assoc {:path []}
          :!viewers
          (r/atom
           {:root
            [{:pred number?
              :fn #(html [:div.inline-block {:style {:width 16 :height 16}
                                             :class (if (pos? %) "bg-black" "bg-white border-solid border-2 border-
black")}])}
             {:pred vector? :fn #(html (into [:div.flex.inline-flex] (inspect-children %2) %1))}
             {:pred list? :fn #(html (into [:div.flex.flex-col] (inspect-children %2) %1))}]}))])

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
       [lazy-inspect-in-process x]]
      [:div.mb-4.overflow-x-hidden
       [inspect x {:path []
                   :expanded-at (r/atom {[] true
                                         [0] true
                                         [1] true
                                         [0 0] true})}]]
      #_#_[:div.mb-4.overflow-x-hidden
           [inspect x]]
      [:div.mb-4.overflow-x-hidden
       [inspect y]]]]))

(defn ^:export ^:dev/after-load mount []
  (when-let [el (js/document.getElementById "clerk")]
    (rdom/render [root] el)))
