(ns nextjournal.clerk.render.editor
  (:require ["@codemirror/autocomplete" :refer [autocompletion]]
            ["@codemirror/language" :refer [syntaxTree]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/view" :refer [keymap placeholder]]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.render.code :as code]
            [nextjournal.clerk.render.hooks :as hooks]
            [nextjournal.clerk.sci-env.completions :as sci-completions]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clojure-mode.extensions.eval-region :as eval-region]
            [nextjournal.clojure-mode.keymap :as clojure-mode.keymap]
            [sci.core :as sci]
            [sci.ctx-store]
            [shadow.esm]))

(defn eval-string
  ([source] (sci/eval-string* (sci.ctx-store/get-ctx) source))
  ([ctx source]
   (when-some [code (not-empty (str/trim source))]
     (try {:result (sci/eval-string* ctx code)}
          (catch js/Error e
            {:error (str (.-message e))})))))

(j/defn eval-at-cursor [on-result ^:js {:keys [state]}]
  (some->> (eval-region/cursor-node-string state)
           (eval-string)
           (on-result))
  true)

(j/defn eval-top-level [on-result ^:js {:keys [state]}]
  (some->> (eval-region/top-level-string state)
           (eval-string)
           (on-result))
  true)

(j/defn eval-cell [on-result ^:js {:keys [state]}]
  (-> (.-doc state)
      (str)
      (eval-string)
      (on-result))
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

(defn eval-notebook [code]
  (as-> code doc
    (parser/parse-clojure-string {:doc? true} doc)
    (update doc :blocks (partial map (fn [{:as b :keys [type text]}]
                                       (cond-> b
                                         (= :code type)
                                         (assoc :result                                                
                                                {:nextjournal/value (eval (render/read-string text))})))))
    (v/with-viewer v/notebook-viewer {:nextjournal.clerk/width :wide} doc)))

(defonce bar-height 26)

(defn view [code-string]
  (let [!notebook (hooks/use-state nil)
        !eval-result (hooks/use-state nil)
        !container-el (hooks/use-ref nil)
        !info (hooks/use-state nil)
        !show-docstring? (hooks/use-state false)
        !view (hooks/use-ref nil)
        on-result #(reset! !eval-result %)
        on-eval #(reset! !notebook (try
                                     (eval-notebook (.. % -state -doc toString))
                                     (catch js/Error error (v/html [render/error-view error]))))]
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
                                                                   :shift (partial eval-top-level on-result)
                                                                   :run (partial eval-at-cursor on-result)}
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
       [:div.bg-slate-200.border-r.border-slate-300.dark:border-slate-600.px-4.py-3.dark:bg-slate-950.overflow-y-auto
        {:class "w-[50vw]" :style {:height (str "calc(100vh - " (* bar-height 2) "px)")}}
        [:div.h-screen {:ref !container-el}]]
       [:div.bg-white.dark:bg-slate-950.bg-white.flex.flex-col.overflow-y-auto
        {:class "w-[50vw]" :style {:height (str "calc(100vh - " (* bar-height 2) "px)")}}
        (when-let [notebook @!notebook]
          [:> render/ErrorBoundary {:hash (gensym)}
           [render/inspect notebook]])]]
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
        [:div.border-t.border-slate-300.px-4.py-2.flex-shrink-0.absolute.left-0.w-screen.bg-white
         {:style {:box-shadow "0 -2px 3px 0 rgb(0 0 0 / 0.025)" :bottom (* bar-height 2)}}
         [render/inspect result]])]]))
