(ns nextjournal.clerk.html
  "Server-side rendering of presented values to hiccup.

  Mirrors the markup of the ClojureScript render functions in
  `nextjournal.clerk.render` so the same CSS applies. Interactivity is
  injected through callbacks in the render context `ctx`, which keeps
  this namespace independent of the transport (Datastar, static build).

  Viewers opt into server-side rendering with a `:render-hiccup` function
  (or a qualified symbol referencing one), which is called with the
  presented value and returns hiccup. Values whose viewer has no `:render-hiccup`
  need the browser and render as islands.

  The render context is passed along in the presented value under `:nextjournal/ctx`
  (use `inspect` with a `ctx` to render children). Its keys are:

  * `:result-state` `(fn [result-id presented auto-expand?] {:presented .. :expanded-at ..})`
  * `:on-toggle`    `(fn [ctx path] attrs)` attrs for an expand button
  * `:on-fetch`     `(fn [ctx fetch-opts] attrs)` attrs for an elision
  * `:on-sync`      `(fn [ctx sync-var coerce value] attrs)` attrs for an input updating a synced
                    var, responsible for setting the input's `value`
  * `:islands?`     render values whose viewers need the browser as `<clerk-island>`s
                    instead of a placeholder"
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [nextjournal.clerk.viewer :as v])
  (:import (java.net URLEncoder)
           (java.nio.charset StandardCharsets)))

(def nbsp " ")

(defn viewer-render-hiccup
  "Returns the `:render-hiccup` of `viewer`, a fn or a qualified symbol. Processed
  viewers keep fns in their metadata so they can be printed and hashed."
  [viewer]
  (or (-> viewer meta :nextjournal/render-hiccup)
      (:render-hiccup viewer)))

(def ^:private default-renderers
  (memoize (fn [viewers]
             (into {} (map (juxt :name (fn [viewer] {:render-fn (:render-fn viewer)
                                                     :render-hiccup (viewer-render-hiccup viewer)})))
                   viewers))))

(defn custom-render-fn?
  "Is `viewer` a default viewer whose `:render-fn` was replaced without
  also replacing its `:render-hiccup`, e.g. via `(assoc v/render-eval-viewer :render-fn ...)`?
  Its `:render-hiccup` would then render something else, so it needs the browser."
  [{:as viewer viewer-name :name :keys [render-fn]}]
  (when-let [default (get (default-renderers (v/get-default-viewers)) viewer-name)]
    (and (not= (:form render-fn render-fn) (:render-fn default))
         (= (viewer-render-hiccup viewer) (:render-hiccup default)))))

(defn render-hiccup-fn
  "Returns the function rendering the presented value `x` to hiccup, resolved from its viewer's `:render-hiccup`."
  [{:nextjournal/keys [viewer]}]
  (when-let [render-hiccup (viewer-render-hiccup viewer)]
    (when-not (custom-render-fn? viewer)
      (if (symbol? render-hiccup)
        (or (requiring-resolve render-hiccup)
            (throw (ex-info (str "Could not resolve `:render-hiccup` " render-hiccup ".") {:viewer viewer})))
        render-hiccup))))

(declare unsupported)

(defn inspect
  "Renders the presented value `x` to hiccup, passing the render context `ctx` along in it under `:nextjournal/ctx`."
  ([x] (inspect (:nextjournal/ctx x) x))
  ([ctx x]
   (when x
     (let [x (assoc x :nextjournal/ctx ctx)]
       (if-let [render-hiccup (render-hiccup-fn x)]
         (render-hiccup x)
         (unsupported ctx x))))))

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
                    (= :<> tag) (doall (map (partial normalize-hiccup ctx) more))
                    (or (keyword? tag) (string? tag))
                    (let [[attrs children] (if (map? (first more)) [(first more) (rest more)] [nil more])
                          inner-html (some-> attrs :dangerouslySetInnerHTML :__html)]
                      (into [(keyword tag)]
                            (cond-> []
                              attrs (conj (normalize-attrs attrs))
                              inner-html (conj (h/raw inner-html))
                              (not inner-html) (into (map (partial normalize-hiccup ctx)) children))))
                    :else (throw (ex-info "hiccup with components needs the browser renderer" {::needs-browser true :tag tag}))))
    (seq? x) (doall (map (partial normalize-hiccup ctx) x))
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

;; ### Clojure syntax highlighting
;; A small tokenizer producing the same `cmt-*` classes as the CodeMirror
;; based highlighting in the browser. Never fails, unknown input is passed through.

(def ^:private token-types
  [[:comment ";[^\\n]*"]
   ;; "unrolled" with possessive quantifiers, `(?:\\.|[^"])*` recurses per char and overflows the stack on long strings
   [:regexp "#\"[^\"\\\\]*+(?:\\\\.[^\"\\\\]*+)*+\"?"]
   [:string "\"[^\"\\\\]*+(?:\\\\.[^\"\\\\]*+)*+\"?"]
   [:char "\\\\(?:newline|space|tab|formfeed|backspace|return|u[0-9a-fA-F]{4}|o[0-7]{1,3}|.)"]
   [:keyword "::?[^\\s,()\\[\\]{}\"`;@^~\\\\]+"]
   [:number "[-+]?\\d[^\\s,()\\[\\]{}\"`;@^~\\\\]*"]
   [:meta "\\^"]
   [:open "#?[(\\[{]|#\\?@?\\(|#_|#'"]
   [:symbol "[^\\s,()\\[\\]{}\"`;@^~\\\\#']+[^\\s,()\\[\\]{}\"`;@^~\\\\]*"]
   [:other "[\\s\\S]"]])

(def ^:private token-re
  (re-pattern (str/join "|" (map #(str "(" (second %) ")") token-types))))

(defn tokenize-clojure
  "Splits `code` into `[css-class text]` tokens, concatenating the texts returns `code`."
  [code]
  (first
   (reduce (fn [[tokens {:as state :keys [after-open? after-def?]}] [text & groups]]
             (let [type (first (keep-indexed (fn [i g] (when g (first (nth token-types i)))) groups))
                   def-like? (and (= :symbol type) after-open? (or (str/starts-with? text "def") (= "ns" text)))
                   class (case type
                           :comment "cmt-comment"
                           :regexp "cmt-regexp"
                           (:string :char) "cmt-string"
                           :keyword "cmt-atom"
                           :meta "cmt-meta"
                           :symbol (cond def-like? "cmt-keyword"
                                         after-def? "cmt-variableName"
                                         (#{"true" "false"} text) "cmt-bool"
                                         (= "nil" text) "cmt-atom")
                           nil)
                   whitespace? (and (= :other type) (str/blank? text))]
               [(conj tokens [class text])
                (cond
                  (or whitespace? (#{:comment :meta :keyword} type)) state
                  :else {:after-open? (and (= :open type) (str/ends-with? text "("))
                         :after-def? def-like?})]))
           [[] {}]
           (re-seq token-re code))))

(defn tokens->lines
  "Splits highlighted tokens into lines of hiccup."
  [tokens]
  (reduce (fn [lines [class text]]
            (let [[line & more-lines] (str/split text #"\n" -1)
                  ->hiccup (fn [s] (if (and class (seq s)) [:span {:class class} s] s))]
              (into (update lines (dec (count lines)) conj (->hiccup line))
                    (map (fn [s] [(->hiccup s)]))
                    more-lines)))
          [[]]
          tokens))

(def ^:private clojure-languages #{"clojure" "clojurescript" "clj" "cljs" "cljc" "edn"})

(defn code-markup
  ([code] (code-markup code "clojure"))
  ([code language]
   (let [code (str/trim-newline code)
         lines (if (clojure-languages language)
                 (tokens->lines (tokenize-clojure code))
                 (map vector (str/split code #"\n" -1)))]
     [:div.cm-editor
      [:div.cm-scroller
       (into [:div.cm-content.whitespace-pre]
             (map (fn [line] (into [:div.cm-line] (if (every? #(and (string? %) (str/blank? %)) line) ["\n"] line))))
             lines)]])))

;; ### JSON
;; Data for client components is passed as JSON attributes.

(defn ^:private json-str [s]
  (str "\"" (str/escape s (fn [c]
                            (case c
                              \" "\\\""
                              \\ "\\\\"
                              \newline "\\n"
                              \return "\\r"
                              \tab "\\t"
                              (when (< (int c) 0x20) (format "\\u%04x" (int c))))))
       "\""))

(defn ->json
  "Serializes `x` to JSON, keywords become strings without their namespace like `clj->js` does."
  [x]
  (cond
    (nil? x) "null"
    (boolean? x) (str x)
    (number? x) (let [d (double x)]
                  (cond (or (Double/isNaN d) (Double/isInfinite d)) "null"
                        (or (integer? x) (== d (Math/rint d))) (if (integer? x) (str (bigint x)) (str (long d)))
                        :else (str d)))
    (string? x) (json-str x)
    (keyword? x) (json-str (name x))
    (map? x) (str "{" (str/join "," (map (fn [[k v]] (str (json-str (if (keyword? k) (name k) (str k))) ":" (->json v))) x)) "}")
    (or (sequential? x) (set? x)) (str "[" (str/join "," (map ->json x)) "]")
    :else (json-str (str x))))

(defn unsupported* [{:as x :nextjournal/keys [viewer]}]
  [:div.text-xs.font-sans.text-slate-400.italic
   (str (if (custom-render-fn? viewer)
          (str "A custom `:render-fn` for `" (:name viewer) "`")
          (str "`" (or (:name viewer) (some-> viewer :render-fn :form)) "`"))
        " requires the browser renderer which isn't available here.")])

(defn island
  "Renders the presented value `x` with Clerk's ClojureScript viewer bundle in the browser."
  [{:keys [result-id]} x]
  (let [presented (v/->edn (dissoc x :nextjournal/ctx))]
    [:clerk-island {:id (str "clerk-island-" (Integer/toUnsignedString (hash [result-id (:path x) presented]) 36))
                    :data-ignore true
                    :data-ignore-morph true
                    :presented presented}]))

(defn unsupported
  "Renders `x` as an island when enabled, a placeholder otherwise."
  ([x] (unsupported* x))
  ([ctx x] (if (:islands? ctx) (island ctx x) (unsupported* x))))

(defn url-encode [s]
  (URLEncoder/encode (str s) StandardCharsets/UTF_8))

(defn url-for [src]
  (if (string? src)
    src
    (let [{:keys [blob-id]} src]
      (str "/_blob/" blob-id (when-let [opts (seq (dissoc src :blob-id))]
                               (str "?" (str/join "&" (map (fn [[k v]] (str (name k) "=" (url-encode (pr-str v)))) opts))))))))

;; ## Viewers

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

(defn render-result [{:nextjournal/keys [ctx value render-opts]}]
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

(declare dark-mode-toggle)

(defn render-notebook [{:nextjournal/keys [ctx value]}]
  (let [{:keys [blocks header footer doc-css-class sidenotes? error toc]} value]
    [:div.flex
     dark-mode-toggle
     (inspect ctx toc)
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

(defn render-cell [{:nextjournal/keys [ctx value]}]
  (inspect-children ctx value))

(defn render-code-block [{:nextjournal/keys [value render-opts]}]
  [:div.viewer.code-viewer.w-full.max-w-wide {:data-block-id (:id render-opts)}
   (code-markup value)])

(defn render-folded-code-block [{:nextjournal/keys [value render-opts]}]
  ;; native <details> needs no server round-trip; `data-preserve-attr` keeps it open across morphs
  [:details.viewer.code-viewer.w-full.max-w-wide {:data-block-id (:id render-opts) :data-preserve-attr "open"}
   [:summary.font-sans.text-slate-400.cursor-pointer.hover:text-slate-500 {:class "text-[10px]"} "show code"]
   (code-markup value)])

(defn render-code [{:nextjournal/keys [value render-opts]}]
  (code-markup value (:language render-opts)))

(defn render-html [{:as x :nextjournal/keys [ctx value]}]
  (if (string? value)
    [:span (h/raw value)]
    (try (normalize-hiccup ctx value)
         (catch clojure.lang.ExceptionInfo e
           (if (::needs-browser (ex-data e))
             (unsupported ctx x)
             (throw e))))))

(defn render-markdown-node [{:nextjournal/keys [ctx value]}]
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

(defn render-coll [{:as x :nextjournal/keys [ctx value viewer]}]
  (render-coll* ctx x (:opening-paren viewer) (< 1 (count value))))

(defn render-map [{:as x :nextjournal/keys [ctx value]}]
  (render-coll* ctx x "{" (< 1 (count value))))

(defn render-map-entry [{:nextjournal/keys [ctx value]}]
  (interpose " " (inspect-children ctx value)))

(defn render-string-content [ctx s expanded?]
  (map #(if (string? %)
          (interpose (if expanded? [:br] [:span.text-slate-400 "↩︎"]) (str/split-lines %))
          (inspect ctx %))
       (if (string? s) [s] s)))

(defn render-quoted-string [{:nextjournal/keys [ctx value viewer] :keys [path]}]
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

(defn render-string [{:nextjournal/keys [ctx value] :keys [path]}]
  [:span.whitespace-pre (render-string-content ctx value (expanded? ctx path))])

(defn render-number [{:nextjournal/keys [value]}]
  [:span.cmt-number.inspected-value (str value)])

(defn render-number-hex [{:nextjournal/keys [value]}]
  [:span.cmt-number.inspected-value (str "0x" (Long/toHexString (long value)))])

(defn render-char [{:nextjournal/keys [value]}]
  [:span.cmt-string.inspected-value "\\" (str value)])

(defn render-symbol [{:nextjournal/keys [value]}]
  [:span.cmt-keyword.inspected-value (str value)])

(defn render-keyword [{:nextjournal/keys [value]}]
  [:span.cmt-atom.inspected-value (str value)])

(defn render-nil [_x]
  [:span.cmt-default.inspected-value "nil"])

(defn render-boolean [{:nextjournal/keys [value]}]
  [:span.cmt-bool.inspected-value (str value)])

(defn render-var [{:nextjournal/keys [value]}]
  [:span.inspected-value [:span.cmt-meta "#'" (str value)]])

(defn render-read+inspect [{:nextjournal/keys [value]}]
  [:span.inspected-value.whitespace-nowrap.cmt-default value])

(defn render-tagged-value [{:keys [space?]} tag value]
  [:span.inspected-value.whitespace-nowrap
   [:span.cmt-meta tag] (when space? nbsp) value])

(defn render-tagged-value-viewer [{:nextjournal/keys [ctx] {:keys [tag value space?]} :nextjournal/value}]
  (render-tagged-value {:space? (:nextjournal/value space?)}
                       (str "#" (:nextjournal/value tag))
                       (inspect ctx value)))

(defn render-elision [{:nextjournal/keys [ctx value]}]
  (let [{:keys [total offset unbounded?]} value
        attrs (fetch-attrs ctx value)]
    [:span.sans-serif.relative.whitespace-nowrap
     (merge {:style (style->str {:border-radius 2 :padding (when attrs "1px 3px") :font-size 11 :top -1})
             :class (if attrs
                      "cursor-pointer bg-indigo-200 hover:bg-indigo-300 dark:bg-gray-700 dark:hover:bg-slate-600 text-gray-900 dark:text-white"
                      "text-gray-400 dark:text-slate-300")}
            attrs)
     (- total offset) (when unbounded? "+") (if attrs " more…" " more elided")]))

(defn render-throwable [{:nextjournal/keys [ctx value]}]
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

(defn render-image [{:nextjournal/keys [value]}]
  (image-markup value))

(defn render-row [{:nextjournal/keys [ctx value]}]
  (into [:div {:class "md:flex md:flex-row md:gap-4 not-prose"}]
        (map (fn [item] [:div.flex.items-center.justify-center.flex-auto (inspect ctx item)]))
        value))

(defn render-col [{:nextjournal/keys [ctx value]}]
  (into [:div {:class "md:flex md:flex-col md:gap-4 clerk-grid not-prose"}]
        (map (fn [item] [:div.flex.items-center.justify-center (inspect ctx item)]))
        value))

;; ### Tables

(defn render-table-markup [{:nextjournal/keys [ctx value render-opts]}]
  (let [ctx' (assoc ctx :table-opts render-opts)]
    [:div.overflow-x-auto.overflow-y-hidden
     {:style {:width "max-content" :max-width "100%"}}
     (into [:table.text-xs.sans-serif.text-gray-900.dark:text-white.not-prose]
           (inspect-children ctx' value))]))

(defn number-col? [ctx i]
  (let [number-col? (get-in ctx [:table-opts :number-col?])]
    (and (set? number-col?) (number-col? i))))

(defn render-table-head [{:nextjournal/keys [ctx value]}]
  [:thead
   (into [:tr]
         (map-indexed (fn [i {:as header-cell :nextjournal/keys [value]}]
                        [:th.pl-6.pr-2.py-1.align-bottom.font-medium.top-0.z-10.bg-white.dark:bg-slate-900.border-b.border-gray-300.dark:border-slate-700
                         (cond-> {:class (when (number-col? ctx i) "text-right")}
                           (or (string? value) (keyword? value) (symbol? value)) (assoc :title (str value)))
                         (inspect ctx header-cell)]))
         value)])

(defn render-table-body [{:nextjournal/keys [ctx value]}]
  (into [:tbody] (inspect-children ctx value)))

(defn render-table-row [{:nextjournal/keys [ctx value] :keys [path]}]
  (into [:tr.hover:bg-gray-200.dark:hover:bg-slate-700
         {:class (if (even? (peek path)) "bg-black/5 dark:bg-gray-800" "bg-white dark:bg-gray-900")}]
        (map-indexed (fn [idx cell]
                       [:td.pl-6.pr-2.py-1 (when (number-col? ctx idx) {:class "text-right"})
                        (inspect ctx cell)]))
        value))

(defn render-table-elision [{:nextjournal/keys [ctx value]}]
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

(defn render-table-error [{:nextjournal/keys [ctx] [data] :nextjournal/value}]
  [:div.bg-red-100.dark:bg-gray-800.px-6.py-4.rounded-md.text-xs.dark:border-2.dark:border-red-400.not-prose
   [:h4.mt-0.uppercase.text-xs.dark:text-red-400.tracking-wide "Table Error"]
   [:p.mt-4.font-medium "Clerk’s table viewer does not recognize the format of your data:"]
   [:div.mt-2 (inspect ctx data)]])

(defn render-table-number [{:nextjournal/keys [value]}]
  [:span.tabular-nums (str value)])

(defn render-nothing [_x]
  nil)

;; ### Synced atoms & controls

(defn render-eval->var
  "Returns the var a render-eval value refers to, supporting `sym` and `(resolve 'sym)` forms."
  [render-eval]
  (let [form (:form render-eval)
        sym (cond (qualified-symbol? form) form
                  (and (seq? form) (= 'resolve (first form))) (let [[_ [_quote sym]] form] sym))]
    (when (qualified-symbol? sym)
      (resolve sym))))

(defn input-value-attrs
  "Attrs setting the `value` of an input bound to `sync-var`, delegating to `:on-sync` when given."
  [{:as ctx :keys [on-sync]} sync-var coerce value]
  (if on-sync
    (on-sync ctx sync-var coerce value)
    {:value value :disabled true}))

(defn render-eval-value [{:as x :nextjournal/keys [ctx value]}]
  (let [var (render-eval->var value)
        ref (some-> var deref)]
    (if (instance? clojure.lang.IDeref ref)
      (render-tagged-value {:space? false} "#object"
                           (inspect (-> ctx (dissoc :on-toggle :on-fetch) (assoc :expanded-at {}))
                                    (v/present [(symbol (.getName (class ref))) @ref])))
      (unsupported ctx x))))

(defn render-slider [{:as x :nextjournal/keys [ctx value render-opts]}]
  (let [{:keys [min max step] :or {min 0 max 100 step 1}} render-opts
        var (render-eval->var value)
        state (some-> var deref deref)]
    (if-not (number? state)
      (unsupported ctx x)
      [:div.inline-flex.items-center.font-sans.pt-3
       [:span.text-slate-500.mr-1 {:class "text-[11px] -mt-[3px]"} (str min)]
       [:div.relative
        [:input (merge {:type "range" :min min :max max :step step}
                       (input-value-attrs ctx var :number state))]
        [:output.absolute.top-0.text-center
         {:class "-translate-x-1/2 -translate-y-full text-[11px] min-w-[25px]"
          :style (str "left:" (double (+ 5 (* (/ (- (clojure.core/min state max) min) (- max min)) 90))) "%")}
         (str state)]]
       [:span.text-slate-500.ml-1 {:class "text-[11px] -mt-[3px]"} (str max)]])))

(defn render-text-input [{:as x :nextjournal/keys [ctx value render-opts]}]
  (let [{:keys [placeholder] :or {placeholder "⌨️"}} render-opts
        var (render-eval->var value)
        state (some-> var deref deref)]
    (if-not (string? state)
      (unsupported ctx x)
      [:input (merge {:type "text"
                      :placeholder placeholder
                      :class "px-3 py-3 placeholder-blueGray-300 text-blueGray-600 relative bg-white bg-white rounded text-sm border border-blueGray-300 outline-none focus:outline-none focus:ring w-full"}
                     (input-value-attrs ctx var :string state))])))

;; ### Client components
;; Rendered by custom elements defined in `nextjournal/clerk/datastar/components.js`.
;; The `id` changes with the content so a morph only replaces them when needed.

(defn component [tag {:keys [result-id]} {:keys [path]} attrs & children]
  (into [tag (merge {:id (str (name tag) "-" (Integer/toUnsignedString (hash [result-id path attrs]) 36))
                     ;; keep Datastar away from the DOM the libraries create
                     :data-ignore true
                     :data-ignore-morph true}
                    attrs)]
        children))

(defn render-vega-lite [{:as x :nextjournal/keys [ctx value]}]
  [:div.overflow-x-auto
   (component :clerk-vega-lite ctx x {:spec (->json (dissoc value :embed/opts :embed/callback))
                                      :opts (->json (get value :embed/opts {}))})])

(defn render-plotly [{:as x :nextjournal/keys [ctx value]}]
  [:div.overflow-x-auto
   (component :clerk-plotly ctx x {:figure (->json value)})])

(defn render-mathjax [{:as x :nextjournal/keys [ctx value]}]
  [:div.overflow-x-auto
   (component :clerk-mathjax ctx x {:tex value} value)])

(defn render-katex [{:as x :nextjournal/keys [ctx value]}]
  (component :clerk-katex ctx x {:tex value :class "block"} value))

(defn render-inline-katex [{:as x :nextjournal/keys [ctx value]}]
  (component :clerk-katex ctx x {:tex value :inline true} value))

;; ### Table of contents & dark mode
;; Whether the toc is open and dark mode are per browser ui state kept in
;; localStorage by `<clerk-toc>` and `clerkToggleDarkMode` in components.js.

(defn render-toc-items [items]
  (into [:div]
        (map (fn [{:keys [emoji path title items]}]
               [:div.text-base.leading-normal.dark:text-white {:class "md:text-[14px]"}
                [:a.flex.flex-auto.gap-1.py-1.rounded.hover:bg-slate-200.dark:hover:bg-slate-900.hover:text-indigo-700.dark:hover:text-white.hover:underline.decoration-indigo-300.dark:decoration-slate-400.underline-offset-2.transition
                 {:class "px-[6px] ml-[8px] mr-[4px]" :href path}
                 (when emoji [:span.flex-shrink-0 emoji])
                 [:span (or title (str/capitalize (last (str/split path #"/"))))]]
                (when (seq items)
                  [:div.relative {:class "ml-[19px]"} (render-toc-items items)])]))
        items))

(defn render-toc [{:nextjournal/keys [value render-opts]}]
  (let [{:keys [toc-visibility]} render-opts]
    (when (and toc-visibility (seq value))
      [:clerk-toc {:data-default (if (= :collapsed toc-visibility) "closed" "open")}
       [:div#clerk-toc-open-button.toc-toggle
        {:class "z-10 fixed right-2 top-2 md:right-auto md:left-3 md:top-[7px] text-slate-400 font-sans text-xs hover:underline cursor-pointer flex items-center bg-white dark:bg-gray-900 py-1 px-3 md:p-0 rounded-full md:rounded-none border md:border-0 border-slate-200 dark:border-gray-500 shadow md:shadow-none dark:text-slate-400 dark:hover:text-white"
         :onclick "clerkToggleToc(true)"}
        [:svg {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :width 20 :height 20}
         [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 6h16M4 12h16M4 18h16"}]]
        [:span.uppercase.tracking-wider.ml-1.font-bold {:class "text-[12px]"} "ToC"]]
       [:div#clerk-toc-panel.bg-slate-100.dark:bg-gray-800.font-sans.border-r.dark:border-slate-900
        [:div.toc-toggle.rounded.hover:bg-slate-200.dark:hover:bg-slate-900.p-1.text-slate-500.hover:text-slate-600.dark:hover:text-white.absolute.right-2.cursor-pointer.z-10
         {:class "top-[11px] -mt-1 -mr-1" :onclick "clerkToggleToc(false)"}
         [:svg.w-4.h-4 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :stroke-width "2"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M15 19l-7-7 7-7"}]]]
        [:div.absolute.left-0.top-0.w-full.h-full.overflow-x-hidden.overflow-y-auto.py-3
         [:div.px-3.mb-1.mt-1.md:mt-0.text-xs.uppercase.tracking-wider.text-slate-500.dark:text-slate-400.font-medium.leading-none
          {:class "md:text-[12px]"} "TOC"]
         (render-toc-items value)]]])))

(def dark-mode-toggle
  [:div.fixed.top-2.left-2.md:left-auto.md:right-2.z-10
   [:button.text-slate-400.hover:text-slate-600.dark:hover:text-white.cursor-pointer
    {:onclick "clerkToggleDarkMode()" :title "Toggle dark mode"}
    [:svg.w-5.h-5.md:w-4.md:h-4.dark:hidden {:viewBox "0 0 50 50" :xmlns "http://www.w3.org/2000/svg"}
     [:path {:fill "currentColor" :d "M 43.81 29.354 C 43.688 28.958 43.413 28.626 43.046 28.432 C 42.679 28.238 42.251 28.198 41.854 28.321 C 36.161 29.886 30.067 28.272 25.894 24.096 C 21.722 19.92 20.113 13.824 21.683 8.133 C 21.848 7.582 21.697 6.985 21.29 6.578 C 20.884 6.172 20.287 6.022 19.736 6.187 C 10.659 8.728 4.691 17.389 5.55 26.776 C 6.408 36.163 13.847 43.598 23.235 44.451 C 32.622 45.304 41.28 39.332 43.816 30.253 C 43.902 29.96 43.9 29.647 43.81 29.354 Z"}]]
    [:svg.w-5.h-5.md:w-4.md:h-4.hidden.dark:block {:viewBox "0 0 24 24" :xmlns "http://www.w3.org/2000/svg" :fill "currentColor"}
     [:circle {:cx "12" :cy "12" :r "5.75"}]
     (for [[cx cy] [[3.09 6.86] [3.09 17.14] [12 22.29] [20.91 17.14] [20.91 6.86] [12 1.71]]]
       [:circle {:cx (str cx) :cy (str cy) :r "1.71"}])]]])

;; ## Status

(defn render-status [{:keys [progress status]}]
  [:div.fixed.w-full.z-20.top-0.left-0
   [:div.w-full.bg-purple-200.dark:bg-purple-900.rounded.z-20 {:class "h-[2px]"}
    [:div.bg-purple-600.dark:bg-purple-400 {:class "h-[2px]" :style {:width (str (* (or progress 0) 100) "%")}}]
    [:div.absolute.text-purple-600.dark:text-white.text-xs.font-sans.ml-1.bg-white.dark:bg-purple-900.rounded-full.shadow.z-20.font-bold.px-2.border.border-slate-300.dark:border-purple-400
     {:style {:font-size "0.5rem"} :class "left-[35px] md:left-0 mt-[7px] md:mt-1"}
     status]]])
