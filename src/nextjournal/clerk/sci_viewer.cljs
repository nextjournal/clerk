(ns nextjournal.clerk.sci-viewer
  (:require [applied-science.js-interop :as j]
            [cljs.reader]
            [clojure.string :as str]
            [devtools]
            [edamame.core :as edamame]
            [goog.object]
            [goog.string :as gstring]
            [nextjournal.clerk.viewer :as viewer :refer [code html md plotly tex vl with-viewer*  with-viewers*] :rename {with-viewer* with-viewer with-viewers* with-viewers}]
            [nextjournal.devcards :as dc]
            [nextjournal.devcards.main]
            [nextjournal.viewer.code :as code]
            [nextjournal.viewer.katex :as katex]
            [nextjournal.viewer.markdown :as markdown]
            [nextjournal.viewer.mathjax :as mathjax]
            [nextjournal.viewer.plotly :as plotly]
            [nextjournal.viewer.table :as table]
            [nextjournal.viewer.vega-lite :as vega-lite]
            [nextjournal.view.context :as view-context]
            [re-frame.context :as rf]
            [react :as react]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.ratom :as ratom]
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
(declare named-viewers)

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



(defn notebook [xs]
  (html
   (into [:div.flex.flex-col.items-center.viewer-notebook]
         (map (fn [x]
                (let [viewer (:viewer (viewer/value x) (viewer/viewer x))
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

(defn ^:export read-string [s]
  (edamame/parse-string s {:all true
                           :read-cond :allow
                           :readers {'file (partial with-viewer :file)
                                     'object (partial with-viewer :object)
                                     'function+ viewer/form->fn+form}
                           :features #{:clj}}))

(defn opts->query [opts]
  (->> opts
       (map #(update % 0 name))
       (map (partial str/join "="))
       (str/join "&")))

#_(opts->query {:s 10 :num 42})

(defn unreadable-edn [edn]
  (html [:span.inspected-value.whitespace-nowrap.text-gray-700 edn]))

(defn inline-result [{:keys [edn string]} _opts]
  (if edn
    (try
      (html [inspect (read-string edn)])
      (catch js/Error _e
        (unreadable-edn edn)))
    (unreadable-edn string)))

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

(defn coll-viewer [{:keys [open]} xs {:as opts :keys [path viewer !expanded-at]}]
  (html (let [expanded? (@!expanded-at path)]
          [:span.inspected-value.whitespace-nowrap
           {:class (when expanded? "inline-flex")}
           [:span
            [:span.bg-opacity-70.whitespace-nowrap
             (when (< 1 (count xs))
               {:on-click (partial toggle-expanded !expanded-at path)
                :class "cursor-pointer bg-indigo-50 hover:bg-indigo-100 hover:rounded-sm border-b border-gray-400 hover:border-gray-500"})
             open]
            (into [:<>]
                  (comp (inspect-children opts)
                        (interpose (if expanded? [:<> [:br] nbsp (when (= 2 (count open)) nbsp)] " ")))
                  xs)
            (into [:<>] (:closing-parens viewer))]])))

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

(defn elision-viewer [{:as fetch-opts :keys [remaining unbounded?]} _]
  (html [view-context/consume :fetch-fn
         (fn [fetch-fn]
           [:span.sans-serif.relative.whitespace-nowrap
            {:style {:border-radius 2 :padding (when (fn? fetch-fn) "1px 3px") :font-size 11 :top -1}
             :class (if (fn? fetch-fn)
                      "cursor-pointer bg-indigo-200 hover:bg-indigo-300"
                      "text-gray-400")
             :on-click #(when (fn? fetch-fn)
                          (fetch-fn fetch-opts))} remaining (when unbounded? "+") (if (fn? fetch-fn) " more…" " more elided")])]))

(defn map-viewer [xs {:as opts :keys [path viewer !expanded-at] :or {path []}}]
  (html (let [expanded? (@!expanded-at path)]
          [:span.inspected-value.whitespace-nowrap
           [:span.bg-opacity-70
            (when (expandable? xs)
              {:on-click (partial toggle-expanded !expanded-at path)
               :class "cursor-pointer bg-indigo-50 hover:bg-indigo-100 hover:rounded-sm border-b border-gray-400 hover:border-gray-500"})
            "{"]
           (into [:<>]
                 (comp (inspect-children opts)
                       (interpose (if expanded? [:<> [:br] (repeat (inc (count path)) nbsp)] " ")))
                 xs)
           (into [:<>] (:closing-parens viewer))])))

(defn string-viewer [s opts]
  (html [:span.syntax-string.inspected-value "\"" (into [:<>] (map #(cond-> % (not (string? %)) inspect)) s) "\""]))

(defn tagged-value [tag value]
  [:span.inspected-value.whitespace-nowrap
   [:span.syntax-tag tag] value])

(defn normalize-viewer [x]
  (if-let [viewer (-> x meta :nextjournal/viewer)]
    (with-viewer viewer x)
    x))


(def js-viewers
  [{:pred #(implements? IDeref %) :fn #(tagged-value (-> %1 type pr-str) (inspect (deref %1) %2))}
   {:pred goog/isObject :fn js-object-viewer}
   {:pred array? :fn (partial coll-viewer {:open [:<> [:span.syntax-tag "#js "] "["] :close "]"})}])


(reset! viewer/!viewers
        {:root (into [] (concat viewer/default-viewers js-viewers named-viewers))})

(defonce !doc (ratom/atom nil))
(defonce !viewers viewer/!viewers)

(defn set-viewers! [scope viewers]
  #_(js/console.log :set-viewers! {:scope scope :viewers viewers})
  (swap! !viewers assoc scope (vec viewers))
  'set-viewers!)

(defn error-badge [& content]
  [:div.bg-red-50.rounded-sm.text-xs.text-red-400.px-2.py-1.items-center.sans-serif.inline-flex
   [:svg.h-4.w-4.text-red-400 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :aria-hidden "true"}
    [:path {:fill-rule "evenodd" :d "M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" :clip-rule "evenodd"}]]
   (into [:div.ml-2.font-bold] content)])

(defn error-boundary [& _]
  (let [!error (r/atom nil)]
    (r/create-class
     {:constructor (fn [_ _])
      :component-did-catch (fn [_ e _info]
                             (reset! !error e))
      :get-derived-state-from-error (fn [e]
                                      (reset! !error e)
                                      #js {})
      :reagent-render (fn [& children]
                        (if @!error
                          (error-badge "rendering error") ;; TODO: show error
                          (into [:<>] children)))})))

(declare default-viewers)

(defn render-with-viewer [opts viewer value]
  #_(js/console.log :render-with-viewer {:value value :viewer viewer #_#_ :opts opts})
  (cond (fn? viewer)
        (viewer value opts)

        (map? viewer)
        (render-with-viewer opts (:fn viewer) value)

        (keyword? viewer)
        (if-let [{render-fn :fn :keys [fetch-opts]} (get ;; TODO change back to `viewers`
                                                     (into {} (map (juxt :name identity)) named-viewers) viewer)]
          (if-not render-fn
            (html (error-badge "no render function for viewer named " (str viewer)))
            (let [render-fn (cond-> render-fn (not (fn? render-fn)) *eval*)]
              (render-fn value (assoc opts :fetch-opts fetch-opts))))
          (html (error-badge "cannot find viewer named " (str viewer))))

        (ifn? viewer)
        (render-with-viewer opts viewer value)

        :else
        (html (error-badge "unusable viewer `" (pr-str viewer) "`"))))

(defn guard [x f] (when (f x) x))

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
                               (viewer/select-viewer value all-viewers))]
           (inspect opts (render-with-viewer (assoc opts :viewers all-viewers :viewer viewer)
                                             viewer
                                             value)))))))

(defn fetch! [{:keys [blob-id]} opts]
  #_(js/console.log :fetch! blob-id opts)
  (-> (js/fetch (str "_blob/" blob-id (when (seq opts)
                                        (str "?" (opts->query opts)))))
      (.then #(.text %))
      (.then #(try (read-string %)
                   (catch js/Error _e
                     (unreadable-edn %))))))

(defn inspect-result [result _opts]
  (html (r/with-let [!desc (r/atom nil)
                     fetch-fn (fn [opts]
                                (.then (fetch! result opts)
                                       (fn [more]
                                         (swap! !desc viewer/merge-descriptions more))))
                     _ (.then (fetch! result {})
                              (fn [desc] (reset! !desc desc)))]
          [view-context/provide {:fetch-fn fetch-fn}
           (when (seq @!desc)
             [error-boundary
              [inspect @!desc]])])))

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
   (when-let [value @(rf/subscribe [::blobs :recursive-range])]
     [inspect-paginated value])]
  {::blobs {:vector (vec (range 30))
            :vector-nested [1 [2] 3]
            :vector-nested-taco '[l [l [l [l [🌮] r] r] r] r]
            :list (range 30)
            :recursive-range (map range (range))
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
  (viewer/with-viewer* :code (pr-str e)))


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


(dc/defcard viewer-reagent-atom
  ;; TODO
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
  [inspect @!doc])

(defn ^:export reset-doc [new-doc]
  (doseq [cell (viewer/value new-doc)
          :when (viewer/registration? cell)
          :let [form (viewer/value cell)]]
    (*eval* form))
  (reset! !doc new-doc))

(dc/defcard eval-viewer
  "Viewers that are lists are evaluated using sci."
  [inspect (with-viewer '(fn [x] (v/html [:h3 "Ohai, " x "! 👋"])) "Hans")])


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
  [inspect
   '([0 1 0] [1 0 1])
   {:path []
    :viewers
    [{:pred number?
      :fn #(html [:div.inline-block {:style {:width 16 :height 16}
                                     :class (if (pos? %) "bg-black" "bg-white border-solid border-2 border-
black")}])}
     {:pred vector? :fn #(html (into [:div.flex.inline-flex] (inspect-children %2) %1))}
     {:pred list? :fn #(html (into [:div.flex.flex-col] (inspect-children %2) %1))}]}])

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
      [map-viewer '{1 ● 2 ■ 3 ▲}]]
     [:div.mb-4
      [inspect {[[[[1 2]]]] [1 2]}]]

     [:div
      {:style {:margin-right -12}}
      [:div.mb-4.overflow-x-hidden
       [inspect x]]]]))

(defn ^:export ^:dev/after-load mount []
  (when-let [el (js/document.getElementById "clerk")]
    (rdom/render [root] el)))

(def named-viewers
  [;; named viewers
   {:name :elision :pred map? :fn elision-viewer}
   {:name :latex :pred string? :fn #(html (katex/to-html-string %))}
   {:name :mathjax :pred string? :fn (comp normalize-viewer mathjax/viewer)}
   {:name :html :pred string? :fn #(html [:div {:dangerouslySetInnerHTML {:__html %}}])}
   {:name :hiccup :fn (fn [x _] (r/as-element x))}
   {:name :plotly :pred map? :fn (comp normalize-viewer plotly/viewer)}
   {:name :vega-lite :pred map? :fn (comp normalize-viewer vega-lite/viewer)}
   {:name :markdown :pred string? :fn markdown/viewer}
   {:name :code :pred string? :fn (comp normalize-viewer code/viewer)}
   {:name :reagent :fn #(r/as-element (cond-> % (fn? %) vector))}
   {:name :eval! :fn (constantly 'nextjournal.clerk.viewer/set-viewers!)}
   {:name :table :fn (comp normalize-viewer table/viewer)}
   {:name :object :fn #(html (tagged-value "#object" [inspect %]))}
   {:name :file :fn #(html (tagged-value "#file " [inspect %]))}
   {:name :clerk/notebook :fn notebook}
   {:name :clerk/var :fn var}
   {:name :clerk/inline-result :fn inline-result}
   {:name :clerk/result :fn inspect-result}])

(def sci-viewer-namespace
  {'html html
   'inspect inspect
   'inspect-result 'inspect-result
   'coll-viewer coll-viewer
   'map-viewer map-viewer
   'elision-viewer elision-viewer
   'tagged-value tagged-value
   'inspect-children inspect-children
   'set-viewers! set-viewers!
   'string-viewer string-viewer
   'with-viewer with-viewer
   'with-viewers with-viewers})

(defonce ctx
  (sci/init {:async? true
             :disable-arity-checks true
             :classes {'js goog/global
                       :allow :all}
             :namespaces {'nextjournal.viewer sci-viewer-namespace
                          'v sci-viewer-namespace}}))


(defn eval-form [f]
  (sci/eval-form ctx f))

(set! *eval* eval-form)
(swap! viewer/!viewers update :root viewer/process-fns)
