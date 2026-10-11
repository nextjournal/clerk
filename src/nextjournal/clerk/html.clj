(ns nextjournal.clerk.html
  "Server-side rendering of presented values to hiccup.

  Mirrors the markup of the ClojureScript render functions in
  `nextjournal.clerk.render` so the same CSS applies. Interactivity is
  injected through callbacks in the render context `ctx`, which keeps
  this namespace independent of the transport (Datastar, static build):

  * `:result-state` `(fn [result-id presented auto-expand?] {:presented .. :expanded-at ..})`
  * `:on-toggle`    `(fn [ctx path] attrs)` attrs for an expand button
  * `:on-fetch`     `(fn [ctx fetch-opts] attrs)` attrs for an elision"
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [nextjournal.clerk.viewer :as v])
  (:import (java.net URLEncoder)
           (java.nio.charset StandardCharsets)))

(def nbsp " ")

(defn viewer-key
  "Dispatch key for a presented value: the qualified symbol of its
  `:render-fn` when it names a function, its viewer name otherwise."
  [{:nextjournal/keys [viewer]}]
  (let [form (some-> viewer :render-fn :form)]
    (if (qualified-symbol? form) form (:name viewer))))

(defmulti render-viewer
  "Renders the presented value `x` to hiccup."
  (fn [_ctx x] (viewer-key x)))

(defn inspect [ctx x]
  (when x
    (render-viewer ctx x)))

(defn inspect-children [ctx xs]
  (map (partial inspect ctx) xs))

;; ## Hiccup normalization
;; Presented values contain reagent-flavoured hiccup (`:<>` fragments,
;; class vectors, style maps, nested presented values). Normalize it to
;; plain hiccup that `hiccup2` can render.

(def ^:private unitless-style-props
  #{:flex :flex-grow :flex-shrink :font-weight :line-height :opacity :order :z-index :zoom})

(defn ^:private style->str [style]
  (if (map? style)
    (str/join ";" (keep (fn [[k v]]
                          (when (some? v)
                            (str (name k) ":" (if (and (number? v) (not (unitless-style-props (keyword k))))
                                                (str v "px")
                                                (if (keyword? v) (name v) v)))))
                        style))
    style))

(defn ^:private class->str [class]
  (if (coll? class)
    (str/join " " (keep #(some-> % (cond-> (keyword? %) name)) class))
    class))

(def ^:private attr-renames
  {:class-name :class :default-checked :checked :default-value :value
   :col-span :colspan :row-span :rowspan :html-for :for :tab-index :tabindex})

(defn normalize-attrs [attrs]
  (reduce-kv (fn [m k v]
               (let [k (attr-renames k k)]
                 (cond
                   (or (#{:ref :key :dangerouslySetInnerHTML} k)
                       (str/starts-with? (name k) "on-")
                       (fn? v))
                   m
                   (= :class k) (assoc m k (class->str v))
                   (= :style k) (assoc m k (style->str v))
                   (false? v) m
                   :else (assoc m k v))))
             {}
             attrs))

(defn inspect-fn-form? [x]
  (and (vector? x) (= (v/inspect-fn) (first x))))

(defn normalize-hiccup [ctx x]
  (cond
    (inspect-fn-form? x) (inspect ctx (second x))
    (v/wrapped-value? x) (inspect ctx x)
    (vector? x) (let [[tag & more] x]
                  (cond
                    (= :<> tag) (map (partial normalize-hiccup ctx) more)
                    (or (keyword? tag) (string? tag))
                    (let [[attrs children] (if (map? (first more)) [(first more) (rest more)] [nil more])
                          inner-html (some-> attrs :dangerouslySetInnerHTML :__html)]
                      (into [(keyword tag)]
                            (cond-> []
                              attrs (conj (normalize-attrs attrs))
                              inner-html (conj (h/raw inner-html))
                              (not inner-html) (into (map (partial normalize-hiccup ctx)) children))))
                    :else [:span.text-slate-400 {:title (pr-str tag)} "[component requires browser renderer]"]))
    (seq? x) (map (partial normalize-hiccup ctx) x)
    :else x))

(defn ->html-str
  "Renders hiccup to an HTML string."
  [hiccup]
  (str (h/html {:mode :html} hiccup)))

;; ## Expansion state

(defn ->expanded-at [auto-expand? presented]
  (cond-> presented
    auto-expand? v/assign-content-lengths
    true (-> v/assign-expanded-at (get :nextjournal/expanded-at {}))))

(defn default-result-state [_result-id presented auto-expand?]
  {:presented presented
   :expanded-at (->expanded-at auto-expand? presented)})

(defn expanded? [ctx path]
  (boolean (get-in ctx [:expanded-at path])))

(defn toggle-attrs [{:as ctx :keys [on-toggle]} path]
  (when on-toggle (on-toggle ctx path)))

(defn fetch-attrs [{:as ctx :keys [on-fetch fetchable?]} fetch-opts]
  (when (and on-fetch fetchable?) (on-fetch ctx fetch-opts)))

;; ## Shared components

(defn closing-parens [viewer]
  (let [cp (:closing-paren viewer)]
    (if (string? cp) [cp] cp)))

(defn triangle [expanded?]
  [:svg {:viewBox "0 0 100 100"
         :class (str "w-[7px] h-[7px] fill-current inline-block transition-all mr-[1px] -mt-[2px] "
                     (if expanded? "rotate-180" "rotate-90"))}
   [:polygon {:points "5.9,88.2 50,11.8 94.1,88.2 "}]])

(def triangle-spacer [:span {:class "inline-block w-[8px]"}])

(defn expand-button [ctx opening-paren path]
  [:span.group.hover:bg-indigo-100.rounded-sm.hover:shadow.cursor-pointer
   (toggle-attrs ctx path)
   [:span.text-slate-400.group-hover:text-indigo-700 (triangle (expanded? ctx path))]
   [:span.group-hover:text-indigo-700 opening-paren]])

(defn code-markup [code]
  [:div.cm-editor
   [:div.cm-scroller
    (into [:div.cm-content.whitespace-pre]
          (map (fn [line] [:div.cm-line (if (str/blank? line) "\n" line)]))
          (str/split-lines code))]])

(defn unsupported [x]
  [:div.text-xs.font-sans.text-slate-400.italic
   (str "`" (viewer-key x) "` requires the browser renderer, not yet supported with `:frontend :datastar`.")])

(defn url-encode [s]
  (URLEncoder/encode (str s) StandardCharsets/UTF_8))

(defn url-for [src]
  (if (string? src)
    src
    (let [{:keys [blob-id]} src]
      (str "/_blob/" blob-id (when-let [opts (seq (dissoc src :blob-id))]
                               (str "?" (str/join "&" (map (fn [[k v]] (str (name k) "=" (url-encode (pr-str v)))) opts))))))))

;; ## Viewers

(defmethod render-viewer :default [_ctx x]
  (unsupported x))

(defn ^:private result-css-class [x]
  (let [{viewer-name :name} (v/->viewer x)
        viewer-css-class (v/css-class x)
        inner-viewer-name (some-> x v/->value v/->viewer :name)]
    (if viewer-css-class
      (cond-> viewer-css-class (string? viewer-css-class) vector)
      ["viewer"
       (when (get-in x [:nextjournal/render-opts :fragment-item?]) "fragment-item")
       (when viewer-name (name viewer-name))
       (when inner-viewer-name (name inner-viewer-name))
       (case (or (v/width x)
                 (case viewer-name
                   (nextjournal.clerk.viewer/code-viewer) :wide
                   (nextjournal.clerk.viewer/markdown-node-viewer) :nested-prose
                   :prose))
         :wide "w-full max-w-wide"
         :full "w-full"
         :nested-prose "w-full max-w-prose"
         "w-full max-w-prose px-8")])))

(defmethod render-viewer 'nextjournal.clerk.render/render-result [ctx {:nextjournal/keys [value render-opts]}]
  (let [{:nextjournal/keys [presented fetch-opts]} value
        {:keys [id auto-expand-results?]} render-opts
        result-state (or (:result-state ctx) default-result-state)
        {:keys [presented expanded-at]} (result-state id presented auto-expand-results?)
        ctx' (assoc ctx :result-id id :expanded-at expanded-at :fetchable? (some? fetch-opts))]
    (when presented
      [:div.result-viewer {:class (class->str (result-css-class presented)) :data-block-id id}
       [:div.relative
        [:div.overflow-x-auto
         (inspect ctx' presented)]]])))

(defmethod render-viewer 'nextjournal.clerk.render/render-notebook [ctx {:nextjournal/keys [value]}]
  (let [{:keys [blocks header footer doc-css-class sidenotes? error]} value]
    [:div.flex
     [:div.flex-auto.w-screen.scroll-container
      (into [:div {:class (class->str (cond-> (or doc-css-class [:flex :flex-col :items-center :notebook-viewer :flex-auto])
                                        sidenotes? (conj :sidenotes-layout)))}]
            (inspect-children ctx (concat (when header [header]) blocks (when footer [footer]))))]
     (when-let [{:nextjournal/keys [presented]} error]
       [:div.fixed.bottom-0.left-0.font-mono.w-screen.z-20
        [:div.flex.ml-7
         [:div.pl-4.pr-3.pt-1.rounded-t.bg-red-100.text-red-600.text-sm.font-bold.relative.border-t.border-l.border-r.border-red-200
          {:style {:bottom "-1px"}} "Errors"]]
        (inspect (assoc ctx :expanded-at {}) presented)])]))

(defmethod render-viewer `v/cell-viewer [ctx {:nextjournal/keys [value]}]
  (inspect-children ctx value))

(defmethod render-viewer 'nextjournal.clerk.render/render-code-block [_ctx {:nextjournal/keys [value render-opts]}]
  [:div.viewer.code-viewer.w-full.max-w-wide {:data-block-id (:id render-opts)}
   (code-markup value)])

(defmethod render-viewer 'nextjournal.clerk.render/render-folded-code-block [_ctx {:nextjournal/keys [value render-opts]}]
  ;; native <details> needs no server round-trip; `data-preserve-attr` keeps it open across morphs
  [:details.viewer.code-viewer.w-full.max-w-wide {:data-block-id (:id render-opts) :data-preserve-attr "open"}
   [:summary.font-sans.text-slate-400.cursor-pointer.hover:text-slate-500 {:class "text-[10px]"} "show code"]
   (code-markup value)])

(defmethod render-viewer 'nextjournal.clerk.render/render-code [_ctx {:nextjournal/keys [value]}]
  (code-markup value))

(defmethod render-viewer 'nextjournal.clerk.render/render-html [ctx {:nextjournal/keys [value]}]
  (if (string? value)
    [:span (h/raw value)]
    (normalize-hiccup ctx value)))

(defmethod render-viewer `v/markdown-node-viewer [ctx {:nextjournal/keys [value]}]
  (normalize-hiccup ctx value))

(defn render-coll* [ctx {:nextjournal/keys [value viewer] :keys [path]} opening-paren expandable?]
  (let [expanded? (expanded? ctx path)]
    [:span.inspected-value.whitespace-nowrap
     {:class (when expanded? "inline-flex")}
     [:span
      (if expandable?
        (expand-button ctx opening-paren path)
        [:span opening-paren])
      (interpose (if expanded? (list [:br] triangle-spacer nbsp (when (= 2 (count opening-paren)) nbsp)) " ")
                 (inspect-children ctx value))
      [:span (apply str (closing-parens viewer))]]]))

(defmethod render-viewer 'nextjournal.clerk.render/render-coll [ctx {:as x :nextjournal/keys [value viewer]}]
  (render-coll* ctx x (:opening-paren viewer) (< 1 (count value))))

(defmethod render-viewer 'nextjournal.clerk.render/render-map [ctx {:as x :nextjournal/keys [value]}]
  (render-coll* ctx x "{" (< 1 (count value))))

(defmethod render-viewer `v/map-entry-viewer [ctx {:nextjournal/keys [value]}]
  (interpose " " (inspect-children ctx value)))

(defn render-string-content [ctx s expanded?]
  (map #(if (string? %)
          (interpose (if expanded? [:br] [:span.text-slate-400 "↩︎"]) (str/split-lines %))
          (inspect ctx %))
       (if (string? s) [s] s)))

(defmethod render-viewer 'nextjournal.clerk.render/render-quoted-string [ctx {:nextjournal/keys [value viewer] :keys [path]}]
  (let [{:keys [opening-paren]} viewer
        [closing-paren & more-closing] (closing-parens viewer)
        multi-line? (some #(and (string? %) (str/includes? % "\n")) (if (string? value) [value] value))]
    [:span.inspected-value.inline-flex
     [:span.cmt-string
      (if multi-line?
        (expand-button ctx opening-paren path)
        [:span opening-paren])]
     [:div
      [:span.cmt-string (render-string-content ctx value (expanded? ctx path)) closing-paren]
      (apply str more-closing)]]))

(defmethod render-viewer 'nextjournal.clerk.render/render-string [ctx {:nextjournal/keys [value] :keys [path]}]
  [:span.whitespace-pre (render-string-content ctx value (expanded? ctx path))])

(defmethod render-viewer 'nextjournal.clerk.render/render-number [_ctx {:nextjournal/keys [value]}]
  [:span.cmt-number.inspected-value (str value)])

(defmethod render-viewer `v/number-hex-viewer [_ctx {:nextjournal/keys [value]}]
  [:span.cmt-number.inspected-value (str "0x" (Long/toHexString (long value)))])

(defmethod render-viewer `v/char-viewer [_ctx {:nextjournal/keys [value]}]
  [:span.cmt-string.inspected-value "\\" (str value)])

(defmethod render-viewer `v/symbol-viewer [_ctx {:nextjournal/keys [value]}]
  [:span.cmt-keyword.inspected-value (str value)])

(defmethod render-viewer `v/keyword-viewer [_ctx {:nextjournal/keys [value]}]
  [:span.cmt-atom.inspected-value (str value)])

(defmethod render-viewer `v/nil-viewer [_ctx _x]
  [:span.cmt-default.inspected-value "nil"])

(defmethod render-viewer `v/boolean-viewer [_ctx {:nextjournal/keys [value]}]
  [:span.cmt-bool.inspected-value (str value)])

(defmethod render-viewer `v/var-viewer [_ctx {:nextjournal/keys [value]}]
  [:span.inspected-value [:span.cmt-meta "#'" (str value)]])

(defmethod render-viewer `v/read+inspect-viewer [_ctx {:nextjournal/keys [value]}]
  [:span.inspected-value.whitespace-nowrap.cmt-default value])

(defn render-tagged-value [{:keys [space?]} tag value]
  [:span.inspected-value.whitespace-nowrap
   [:span.cmt-meta tag] (when space? nbsp) value])

(defmethod render-viewer `v/tagged-value-viewer [ctx {{:keys [tag value space?]} :nextjournal/value}]
  (render-tagged-value {:space? (:nextjournal/value space?)}
                       (str "#" (:nextjournal/value tag))
                       (inspect ctx value)))

(defmethod render-viewer 'nextjournal.clerk.render/render-elision [ctx {:nextjournal/keys [value]}]
  (let [{:keys [total offset unbounded?]} value
        attrs (fetch-attrs ctx value)]
    [:span.sans-serif.relative.whitespace-nowrap
     (merge {:style (style->str {:border-radius 2 :padding (when attrs "1px 3px") :font-size 11 :top -1})
             :class (if attrs
                      "cursor-pointer bg-indigo-200 hover:bg-indigo-300 dark:bg-gray-700 dark:hover:bg-slate-600 text-gray-900 dark:text-white"
                      "text-gray-400 dark:text-slate-300")}
            attrs)
     (- total offset) (when unbounded? "+") (if attrs " more…" " more elided")]))

(defmethod render-viewer 'nextjournal.clerk.render/render-throwable [ctx {:nextjournal/keys [value]}]
  (let [{:keys [via trace]} value
        {:keys [type message data]} (first via)]
    [:div.rounded.border.border-red-200.border-t-0.overflow-hidden
     [:div.bg-red-100.text-sm.w-full.border-t.border-red-200.overflow-auto.font-mono
      {:style {:max-height "60vh"}}
      [:div.px-5.py-4.border-t.border-red-200.first:border-t-0
       (when type [:div.font-bold.text-red-600 "Unhandled " (str type)])
       [:div.font-bold.mt-1 message]
       (when data [:div.mt-1 (inspect ctx data)])]
      (when-let [caused-by (not-empty (rest via))]
        [:details.border-t.border-red-200.text-xs.px-5.py-2 {:data-preserve-attr "open"}
         [:summary.text-xs.text-red-600.font-bold.hover:underline.cursor-pointer
          "Causes (" (count caused-by) ")"]
         (for [{:keys [type message data]} caused-by]
           [:div.px-5.mt-3
            (when type [:div.font-bold.text-red-600 "Caused by " (str type)])
            [:div.font-bold.mt-1 message]
            (when data [:div.mt-1 (inspect ctx data)])])])
      (when (seq trace)
        [:details.border-t.border-red-200.text-xs.px-5.py-2 {:data-preserve-attr "open"}
         [:summary.text-xs.text-red-600.font-bold.hover:underline.cursor-pointer "Stacktrace"]
         [:table.w-full.not-prose
          (into [:tbody]
                (map (fn [[call _x file line]]
                       [:tr.hover:bg-red-100.leading-tight
                        [:td.text-right.px-6 (str file) ":"]
                        [:td.text-right.pr-6 (str line)]
                        [:td.py-1.pr-6 (str call)]]))
                trace)]])]]))

(defn image-markup [src]
  [:div.flex.flex-col.items-center.not-prose
   [:img {:src (url-for src)}]])

(defmethod render-viewer `v/image-viewer [_ctx {:nextjournal/keys [value]}]
  (image-markup value))

(defmethod render-viewer `v/row-viewer [ctx {:nextjournal/keys [value]}]
  (into [:div {:class "md:flex md:flex-row md:gap-4 not-prose"}]
        (map (fn [item] [:div.flex.items-center.justify-center.flex-auto (inspect ctx item)]))
        value))

(defmethod render-viewer `v/col-viewer [ctx {:nextjournal/keys [value]}]
  (into [:div {:class "md:flex md:flex-col md:gap-4 clerk-grid not-prose"}]
        (map (fn [item] [:div.flex.items-center.justify-center (inspect ctx item)]))
        value))

;; ### Tables

(defn render-table-markup [ctx {:nextjournal/keys [value render-opts]}]
  (let [ctx' (assoc ctx :table-opts render-opts)]
    [:div.overflow-x-auto.overflow-y-hidden
     {:style {:width "max-content" :max-width "100%"}}
     (into [:table.text-xs.sans-serif.text-gray-900.dark:text-white.not-prose]
           (inspect-children ctx' value))]))

(defn number-col? [ctx i]
  (let [number-col? (get-in ctx [:table-opts :number-col?])]
    (and (set? number-col?) (number-col? i))))

(defn render-table-head [ctx {:nextjournal/keys [value]}]
  [:thead
   (into [:tr]
         (map-indexed (fn [i {:as header-cell :nextjournal/keys [value]}]
                        [:th.pl-6.pr-2.py-1.align-bottom.font-medium.top-0.z-10.bg-white.dark:bg-slate-900.border-b.border-gray-300.dark:border-slate-700
                         (cond-> {:class (when (number-col? ctx i) "text-right")}
                           (or (string? value) (keyword? value) (symbol? value)) (assoc :title (str value)))
                         (inspect ctx header-cell)]))
         value)])

(defn render-table-body [ctx {:nextjournal/keys [value]}]
  (into [:tbody] (inspect-children ctx value)))

(defn render-table-row [ctx {:nextjournal/keys [value] :keys [path]}]
  (into [:tr.hover:bg-gray-200.dark:hover:bg-slate-700
         {:class (if (even? (peek path)) "bg-black/5 dark:bg-gray-800" "bg-white dark:bg-gray-900")}]
        (map-indexed (fn [idx cell]
                       [:td.pl-6.pr-2.py-1 (when (number-col? ctx idx) {:class "text-right"})
                        (inspect ctx cell)]))
        value))

(defn render-table-elision [ctx {:nextjournal/keys [value]}]
  (let [{:keys [total offset unbounded?]} value
        attrs (fetch-attrs ctx value)]
    [:tr.border-t.dark:border-slate-700
     [:td.py-1.relative.text-center
      (merge {:colspan (get-in ctx [:table-opts :num-cols])
              :class (if attrs
                       "bg-indigo-50 hover:bg-indigo-100 dark:bg-gray-800 dark:hover:bg-slate-700 cursor-pointer"
                       "text-gray-400 text-slate-500")}
             attrs)
      (- total offset) (when unbounded? "+") (if attrs " more…" " more elided")]]))

(defn render-table-error [ctx {[data] :nextjournal/value}]
  [:div.bg-red-100.dark:bg-gray-800.px-6.py-4.rounded-md.text-xs.dark:border-2.dark:border-red-400.not-prose
   [:h4.mt-0.uppercase.text-xs.dark:text-red-400.tracking-wide "Table Error"]
   [:p.mt-4.font-medium "Clerk’s table viewer does not recognize the format of your data:"]
   [:div.mt-2 (inspect ctx data)]])

(doseq [[ks f] {['nextjournal.clerk.render.table/render-table-markup `v/table-markup-viewer] render-table-markup
                ['nextjournal.clerk.render.table/render-table-head `v/table-head-viewer] render-table-head
                ['nextjournal.clerk.render.table/render-table-body `v/table-body-viewer] render-table-body
                ['nextjournal.clerk.render.table/render-table-row `v/table-row-viewer] render-table-row
                ['nextjournal.clerk.render.table/render-table-elision] render-table-elision
                ['nextjournal.clerk.render.table/render-table-error `v/table-error-viewer] render-table-error
                ['nextjournal.clerk.render.table/render-table-number] (fn [_ctx {:nextjournal/keys [value]}]
                                                                        [:span.tabular-nums (str value)])
                [`v/table-missing-viewer] (constantly nil)}
        k ks]
  (.addMethod ^clojure.lang.MultiFn render-viewer k f))

;; ### Placeholders until client components land (phase 3)

(defmethod render-viewer 'nextjournal.clerk.render/render-katex [_ctx {:nextjournal/keys [value]}]
  [:div.font-mono.text-center.my-2 value])

(defmethod render-viewer :nextjournal.markdown/formula [_ctx {:nextjournal/keys [value]}]
  [:span.font-mono value])

(defmethod render-viewer `v/toc-viewer [_ctx _x]
  nil)

;; ## Status

(defn render-status [{:keys [progress status]}]
  [:div.fixed.w-full.z-20.top-0.left-0
   [:div.w-full.bg-purple-200.dark:bg-purple-900.rounded.z-20 {:class "h-[2px]"}
    [:div.bg-purple-600.dark:bg-purple-400 {:class "h-[2px]" :style {:width (str (* (or progress 0) 100) "%")}}]
    [:div.absolute.text-purple-600.dark:text-white.text-xs.font-sans.ml-1.bg-white.dark:bg-purple-900.rounded-full.shadow.z-20.font-bold.px-2.border.border-slate-300.dark:border-purple-400
     {:style {:font-size "0.5rem"} :class "left-[35px] md:left-0 mt-[7px] md:mt-1"}
     status]]])
