(ns nextjournal.clerk.render.editor
  (:require ["@codemirror/autocomplete" :refer [autocompletion]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/language" :refer [syntaxTree]]
            ["@codemirror/view" :refer [keymap placeholder hoverTooltip]]
            ["@nextjournal/lang-clojure" :refer [clojureLanguage]]
            ["react" :as react]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.clerk.render.hooks :as hooks]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.render.code :as code]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clojure-mode :as clojure-mode]
            [nextjournal.clojure-mode.keymap :as clojure-mode.keymap]
            [nextjournal.clojure-mode.extensions.eval-region :as eval-region]
            [nextjournal.clerk.sci-env.completions :as sci-completions]
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

(def doc-tooltip
  (hoverTooltip
   (fn [^js view pos side]
     (let [node-before (.. (syntaxTree (.-state view)) (resolveInner pos -1))
           text-at-point (.. view -state (sliceDoc (.-from node-before) (.-to node-before)))
           {:as res :keys [candidated info]} (some->> (sci-completions/completions {:ctx (sci.ctx-store/get-ctx) :ns "user" :symbol text-at-point})
                                                      :completions
                                                      (filter #(= (:candidate %) text-at-point))
                                                      first)]
       (when (and res info)
         (let [{:keys [arglists-str doc name]} info]
           #js {:pos pos
                :create (fn [view]
                          (let [dom (doto (js/document.createElement "div")
                                      (.setAttribute "class" "font-mono text-[11px] p-2"))
                                name-el (doto (js/document.createElement "div")
                                          (.setAttribute "class" "font-bold"))
                                args-el (doto (js/document.createElement "span")
                                          (.setAttribute "class" "ml-2 italic font-normal"))
                                docs-el (doto (js/document.createElement "div")
                                          (.setAttribute "class" "pre-wrap mt-3"))]
                            (set! (.-textContent name-el) (str name))
                            (set! (.-textContent args-el) arglists-str)
                            (.appendChild name-el args-el)
                            (.appendChild dom name-el)
                            (set! (.-textContent docs-el) doc)
                            (.appendChild dom docs-el)
                            #js {:dom dom}))}))))))

(defn eval-notebook [code]
  (as-> code doc
    (nextjournal.clerk.parser/parse-clojure-string {:doc? true} doc)
    (update doc :blocks (partial map (fn [{:as b :keys [type text]}]
                                       (cond-> b
                                         (= :code type)
                                         (assoc :result
                                                {:nextjournal/value
                                                 (let [val (eval (render/read-string text))]
                                                   ;; FIXME: this won't be necessary once we unify v/html in SCI env to be the same as in nextjournal.clerk.viewer
                                                   ;; v/html is currently html-render for supporting legacy render-fns
                                                   (cond->> val
                                                     (render/valid-react-element? val)
                                                     (v/with-viewer v/reagent-viewer)))})))))
    (v/with-viewer v/notebook-viewer {:nextjournal.clerk/width :wide} doc)))



(defn view [code-string]
  (let [!notebook (hooks/use-state nil)
        !eval-result (hooks/use-state nil)
        !container-el (hooks/use-ref nil)
        !view (hooks/use-ref nil)
        on-result #(reset! !eval-result %)
        on-eval #(reset! !notebook (try
                                     (eval-notebook (.. % -state -doc toString))
                                     (catch js/Error error (v/with-viewer v/reagent-viewer
                                                             [render/error-view error]))))]
    (hooks/use-effect
     (fn []
       (let [^js view
             (reset! !view (code/make-view
                            (code/make-state code-string
                                             (.concat code/default-extensions
                                                      #js [(placeholder "Show code with Option+Return")
                                                           (.of keymap clojure-mode.keymap/paredit)
                                                           doc-tooltip
                                                           completion-source
                                                           (.. EditorState -transactionExtender
                                                               (of (fn [^js tr]
                                                                     (when (.-selection tr)
                                                                       (reset! !eval-result nil))
                                                                     #js {})))
                                                           (.of keymap
                                                                (j/lit
                                                                 [{:key "Alt-Enter"
                                                                   :run on-eval}
                                                                  {:key "Mod-Enter"
                                                                   :shift (partial eval-top-level on-result)
                                                                   :run (partial eval-at-cursor on-result)}]))]))
                            @!container-el))]
         (on-eval view)
         #(.destroy view))))
    [:<>
     [:style {:type "text/css"} ".notebook-viewer { padding-top: 2.5rem; } .notebook-viewer .viewer:first-child { display: none; }"]
     [:div.fixed.w-screen.h-screen.flex.flex-col.top-0.left-0
      [:div.flex
       [:div.bg-slate-200.border-r.border-slate-300.dark:border-slate-600.px-4.py-3.dark:bg-slate-800.h-screen
        {:class "w-[50vw]"}
        [:div.h-screen {:ref !container-el}]]
       [:div.bg-white.dark:bg-slate-950.bg-white.flex.flex-col.overflow-y-auto.h-screen
        {:class "w-[50vw]"}
        [render/inspect @!notebook]]]
      (when-let [result @!eval-result]
        [:div.border-t.border-slate-300.px-4.py-2.flex-shrink-0.absolute.bottom-0.left-0.w-screen.bg-white
         {:style {:box-shadow "0 -2px 3px 0 rgb(0 0 0 / 0.025)"}}
         [render/inspect result]])]]))
