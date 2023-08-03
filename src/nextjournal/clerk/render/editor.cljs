(ns nextjournal.clerk.render.editor
  (:require ["@codemirror/autocomplete" :refer [autocompletion]]
            ["@codemirror/language" :refer [syntaxTree]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/view" :refer [keymap placeholder]]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [goog.events]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.render.code :as code]
            [nextjournal.clerk.render.hooks :as hooks]
            [nextjournal.clerk.render.panel :as panel]
            [nextjournal.clerk.sci-env.completions :as sci-completions]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clojure-mode.extensions.eval-region :as eval-region]
            [nextjournal.clojure-mode.keymap :as clojure-mode.keymap]
            [rewrite-clj.node :as n]
            [rewrite-clj.parser :as p]
            [sci.core :as sci]
            [sci.ctx-store]
            [shadow.esm]))

(defn eval-string
  ([source] (eval-string (sci.ctx-store/get-ctx) source))
  ([ctx source]
   (new js/Promise
        (fn [resolve reject]
          (if-some [code (not-empty (str/trim source))]
            (try (resolve (v/present (sci/eval-string* ctx code)))
                 (catch js/Error e
                   (reject e)))
            (resolve nil))))))

(j/defn eval-region* [get-region-fn eval-fn on-result-fn ^:js {:keys [state]}]
  (when-some [code-str (get-region-fn state)]
    (.. (eval-fn code-str)
        (then on-result-fn)
        (catch (fn [error] (on-result-fn (v/present (v/html [render/error-view error])))))))
  true)

(defn autocomplete [^js context]
  (let [node-before (.. (syntaxTree (.-state context)) (resolveInner (.-pos context) -1))
        text-before (.. context -state (sliceDoc (.-from node-before) (.-pos context)))]
    #js {:from (.-from node-before)
         :options (clj->js (map
                            (fn [{:as option :keys [candidate info]}]
                              (let [{:keys [arglists arglists-str]} info]
                                (cond-> {:label candidate :type (if arglists "function" "namespace")}
                                  arglists (assoc :detail arglists-str))))
                            (:completions (sci-completions/completions {:ctx (sci.ctx-store/get-ctx) :ns "user" :symbol text-before}))))}))

(def completion-source
  (autocompletion #js {:override #js [autocomplete]}))

(defn info-at-point [^js view pos]
  (let [node-before (.. (syntaxTree (.-state view)) (resolveInner pos -1))
        text-at-point (.. view -state (sliceDoc (.-from node-before) (.-to node-before)))]
    (some->> (sci-completions/completions {:ctx (sci.ctx-store/get-ctx) :ns "user" :symbol text-at-point})
             :completions
             (filter #(= (:candidate %) text-at-point))
             first
             :info)))

(defn get-block-id [!id->count {:as block :keys [var form type doc]}]
  (let [id->count @!id->count
        id (if var
             var
             (let [hash-fn hash]
               (symbol (str *ns*)
                       (case type
                         :code (str "anon-expr-" (hash-fn form))
                         :markdown (str "markdown-" (hash-fn doc))))))]
    (swap! !id->count update id (fnil inc 0))
    (if (id->count id)
      (symbol (str *ns*) (str (name id) "#" (inc (id->count id))))
      id)))


(defn analyze [form]
  (cond-> {:form form}
    (and (seq? form)
         (str/starts-with? (str (first form)) "def"))
    (assoc :var (second form))))

(defn ns-resolver [notebook-ns]
  (into {} (map (juxt key (comp ns-name val))) '{clerk nextjournal.clerk}))

(defn parse-ns-aliases [ns-form]
  (some (fn [x]
          (when (and (seq? x)
                     (= :require (first x)))
            (into {}
                  (keep (fn [require-form]
                          (when (and (vector? require-form)
                                     (= 3 (count require-form))
                                     (contains? #{:as :as-alias} (second require-form)))
                            ((juxt peek first) require-form))))
                  (rest x))))
        ns-form))

;; TODO: unify with `analyzer/analyze-doc` and move to parser
(defn analyze-doc
  ([doc]
   (analyze-doc {:doc? true} doc))
  ([{:as state :keys [doc?]} doc]
   (binding [*ns* *ns*]
     (let [!id->count (atom {})]
       (cond-> (reduce (fn [{:as state notebook-ns :ns :keys [ns-aliases]} i]
                         (let [{:as block :keys [type text]} (get-in doc [:blocks i])]
                           (if (not= type :code)
                             (assoc-in state [:blocks i :id] (get-block-id !id->count block))
                             (let [node (p/parse-string text)
                                   form (try (n/sexpr node (when ns-aliases {:auto-resolve ns-aliases}))
                                             (catch js/Error e
                                               (throw (ex-info (str "Clerk analysis failed reading block: "
                                                                    (ex-message e))
                                                               {:block block
                                                                :file (:file doc)}
                                                               e))))
                                   analyzed (cond-> (analyze form)
                                              (:file doc) (assoc :file (:file doc)))
                                   block-id (get-block-id !id->count (merge analyzed block))
                                   analyzed (assoc analyzed :id block-id)]
                               (cond-> state
                                 (and (not ns-aliases) (parser/ns? form)) (assoc :ns-aliases (parse-ns-aliases form))
                                 doc? (update-in [:blocks i] merge analyzed)
                                 doc? (assoc-in [:blocks i :text-without-meta]
                                                (parser/text-with-clerk-metadata-removed text (ns-resolver notebook-ns)))
                                 (and doc? (not (contains? state :ns))) (merge (parser/->doc-settings form) {:ns *ns*}))))))
                       (cond-> state
                         doc? (merge doc))
                       (-> doc :blocks count range))
         doc? (-> parser/add-block-settings
                  parser/add-open-graph-metadata
                  parser/filter-code-blocks-without-form))))))

(defn eval-blocks [doc]
  (update doc :blocks (partial map (fn [{:as cell :keys [type text var form]}]
                                     (cond-> cell
                                       (= :code type)
                                       (assoc :result
                                              {:nextjournal/value (cond->> (eval form)
                                                                    var (hash-map :nextjournal.clerk/var-from-def))}))))))

(defn eval-notebook [code]
  (new js/Promise
       (fn [resolve reject]
         (try
           (resolve
             (->> code
                  (parser/parse-clojure-string {:doc? true})
                  (analyze-doc)
                  (eval-blocks)
                  (v/with-viewer v/notebook-viewer)
                  v/present))
           (catch js/Error error
             (reject error))))))

(defonce bar-height 26)

(defn spinner-svg [& [{:keys [size] :or {size 16}}]]
  [:div.flex.items-center.justify-center {:class "w-[50px] h-[50px]"}
   [:svg.animate-spin.text-greenish
    {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24"
     :style {:width size :height size}}
    [:circle.opacity-25 {:cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
    [:path.opacity-75 {:fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]])

(defn view [code-string {:as _opts :keys [eval-notebook-fn eval-string-fn]}]
  (let [!notebook (hooks/use-state nil)
        !eval-result (hooks/use-state nil)
        !container-el (hooks/use-ref nil)
        !info (hooks/use-state nil)
        !show-docstring? (hooks/use-state false)
        !view (hooks/use-ref nil)
        !editor-panel (hooks/use-ref nil)
        !notebook-panel (hooks/use-ref nil)
        on-result #(reset! !eval-result %)
        on-eval (fn [^js editor-view]
                  (.. ((or eval-notebook-fn eval-notebook) (.. editor-view -state -doc toString))
                      (then (fn [result] (reset! !notebook result)))
                      (catch (fn [error]
                               (reset! !notebook (v/present (v/html [render/error-view error])))))))]
    (hooks/use-effect
     (fn []
       (let [^js view
             (reset! !view (code/make-view
                            (code/make-state code-string
                                             (.concat code/default-extensions
                                                      #js [(placeholder "Show code with Option+Return")
                                                           (.of keymap clojure-mode.keymap/paredit)
                                                           completion-source
                                                           (.. EditorState -transactionExtender
                                                               (of (fn [^js tr]
                                                                     (when (.-selection tr)
                                                                       (reset! !eval-result nil)
                                                                       (reset! !show-docstring? false)
                                                                       (reset! !info (some-> (info-at-point @!view (.-to (first (.. tr -selection asSingle -ranges)))))))
                                                                     #js {})))
                                                           (eval-region/extension {:modifier "Meta"})
                                                           (.of keymap
                                                                (j/lit
                                                                 [{:key "Alt-Enter"
                                                                   :run on-eval}
                                                                  {:key "Mod-Enter"
                                                                   :shift (partial eval-region* eval-region/top-level-string (or eval-string-fn eval-string) on-result)
                                                                   :run (partial eval-region* eval-region/cursor-node-string (or eval-string-fn eval-string) on-result)}
                                                                  {:key "Mod-i"
                                                                   :preventDefault true
                                                                   :run #(swap! !show-docstring? not)}
                                                                  {:key "Escape"
                                                                   :run #(reset! !show-docstring? false)}]))]))
                            @!container-el))]
         (on-eval view)
         #(.destroy view))))
    (code/use-dark-mode !view)
    [:<>
     [:style {:type "text/css"} (str ".notebook-viewer { padding-top: 2.5rem; } "
                                     ".notebook-viewer .viewer:first-child { display: none; } "
                                     "#clerk > div > div > .dark-mode-toggle { display: none !important; }")]
     [:div.fixed.w-screen.h-screen.flex.flex-col.top-0.left-0
      [:div.flex
       [:div.relative
        {:ref !editor-panel :style {:width "50vw"}}
        [:div.bg-slate-200.border-r.border-slate-300.dark:border-slate-600.px-4.py-3.dark:bg-slate-950.overflow-y-auto.relative
         {:style {:height (str "calc(100vh - " (* bar-height 2) "px)")}}
         [:div.h-screen {:ref !container-el}]]
        [:div.absolute.right-0.top-0.bottom-0.z-1000.group
         {:class "w-[9px] -mr-[5px]"}
         [:div.absolute.h-full.bg-transparent.group-hover:bg-blue-500.transition.pointer-events-none
          {:class "left-[3px] w-[3px]"}]
         [panel/resizer {:axis :x :on-resize (fn [_ dx _]
                                               (j/assoc-in! @!editor-panel [:style :width] (str (+ (.-offsetWidth @!editor-panel) dx) "px"))
                                               (j/assoc-in! @!notebook-panel [:style :width] (str (- (.-offsetWidth @!notebook-panel) dx) "px")))}]]]
       [:div.bg-white.dark:bg-slate-950.bg-white.flex.flex-col.overflow-y-auto
        {:ref !notebook-panel
         :style {:width "50vw" :height (str "calc(100vh - " (* bar-height 2) "px)")}}
        (if-some [notebook @!notebook]
          [:> render/ErrorBoundary {:hash (gensym)}
           [render/inspect-presented notebook]]
          [:div.flex.flex-col.items-center.justify-items-center.my-10
           [spinner-svg {:size "100px"}]])]]
      [:div.absolute.left-0.bottom-0.w-screen.font-mono.text-white.border-t.dark:border-slate-600
       [:div.bg-slate-900.dark:bg-slate-800.flex.px-4.font-mono.gap-4.items-center.text-white
        {:class "text-[12px]" :style {:height bar-height}}
        [:div.flex.gap-1.items-center
         "Eval notebook"
         [:div.font-inter.text-slate-300 "⌥↩"]]
        [:div.flex.gap-1.items-center
         "Eval at cursor"
         [:div.font-inter.text-slate-300 "⌘↩"]]
        [:div.flex.gap-1.items-center
         "Eval top level"
         [:div.font-inter.text-slate-300 "⇧⌘↩"]]
        [:div.flex.gap-1.items-center
         "Slurp forward"
         [:div.font-inter.text-slate-300 "Ctrl→"]]
        [:div.flex.gap-1.items-center
         "Barf forward"
         [:div.font-inter.text-slate-300 "Ctrl←"]]
        [:div.flex.gap-1.items-center
         "Splice"
         [:div.font-inter.text-slate-300 "⌥S"]]
        [:div.flex.gap-1.items-center
         "Expand selection"
         [:div.font-inter.text-slate-300 "⌘1"]]
        [:div.flex.gap-1.items-center
         "Contract selection"
         [:div.font-inter.text-slate-300 "⌘2"]]]
       [:div.w-screen.bg-slate-800.dark:bg-slate-950.px-4.font-mono.items-center.text-white.flex.items-center
        {:class "text-[12px] py-[4px]" :style {:min-height bar-height}}
        (when-let [{:keys [name arglists-str doc]} @!info]
          [:div
           [:div.flex.gap-4
            [:div.flex.gap-2
             [:span.font-bold (str name)]
             [:span arglists-str]]
            (when (and doc (not @!show-docstring?))
              [:div.flex.gap-1.items-center.text-slate-300
               "Show docstring"
               [:div.font-inter.text-slate-400.flex-shrink-0 "⌘I"]])]
           (when (and doc @!show-docstring?)
             [:div.text-slate-300.mt-2.mb-1.leading-relaxed {:class "max-w-[640px]"} doc])])]]
      (when-let [result @!eval-result]
        [:div.border-t.border-slate-300.dark:border-slate-600.px-4.py-2.flex-shrink-0.absolute.left-0.w-screen.bg-white.dark:bg-slate-950
         {:style {:box-shadow "0 -2px 3px 0 rgb(0 0 0 / 0.025)" :bottom (* bar-height 2)}}
         [render/inspect-presented result]])]]))
