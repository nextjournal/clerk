(ns nextjournal.clerk.markdown-editor
  (:require
   ["katex" :as katex]
   ["@codemirror/language" :refer [defaultHighlightStyle syntaxHighlighting LanguageSupport]]
   ["@codemirror/state" :refer [EditorState]]
   ["@codemirror/view" :refer [EditorView keymap]]
   #_["@codemirror/lang-markdown" :as MD :refer [markdown markdownLanguage]]
   ["react" :as react]
   [nextjournal.markdown :as md]
   [nextjournal.clerk.viewer :as v]
   [nextjournal.clerk.render.hooks :as hooks]
   [nextjournal.markdown.transform :as md.transform]
   [nextjournal.clojure-mode :as clojure-mode]
   [nextjournal.clerk.render.code :as code]
   [reagent.core :as r]
   [clojure.string :as str]
   [sci.ctx-store]
   [nextjournal.clerk.render :as render]))

(def theme #js {"&.cm-editor.cm-focused" #js {:outline "none"}
                ".cm-activeLine" #js {:background-color "rgb(226 232 240)"}
                ".cm-line" #js {:padding "0"
                                :line-height "1.6"
                                :font-size "15px"
                                :font-family "\"Fira Mono\", monospace"}})

;; syntax (an LRParser) + support (a set of extensions)
(def clojure-lang (LanguageSupport. (clojure-mode/syntax)
                                    (.. clojure-mode/default-extensions (slice 1))))
(defn on-change-ext [f]
  (.. EditorState -transactionExtender
      (of (fn [^js tr]
            (when (.-docChanged tr) (f (.. tr -state sliceDoc)))
            #js {}))))

(defn eval-string [source]
  (when-some [code (not-empty (str/trim source))]
    (try {:result  #_:clj-kondo/ignore (load-string source)}
         (catch js/Error e
           {:error (str (.-message e))}))))

(defn editor [{:keys [doc lang editable? on-change] :or {editable? true}}]
  (let [!editor-el (hooks/use-ref)
        extensions (into-array (cond-> [(syntaxHighlighting defaultHighlightStyle)
                                        (.. EditorState -allowMultipleSelections (of editable?))
                                        #_(foldGutter)
                                        (.. EditorView -editable (of editable?))
                                        (.of keymap clojure-mode/complete-keymap)
                                        (.theme EditorView theme)]

                                 on-change
                                 (conj (on-change-ext on-change))

                                 (= :clojure lang)
                                 (conj (.-extension clojure-lang))

                                 #_#_(= :markdown lang)
                                 (conj (markdown #js {:base markdownLanguage
                                                      :defaultCodeLanguage clojure-lang}))))]
    (hooks/use-effect
     (fn []
       (let [editor-view* (code/make-view (code/make-state doc extensions) @!editor-el)]
         #(.destroy editor-view*))) [doc])
    [:div {:ref !editor-el}]))

(defn clojure-editor [{:as opts :keys [doc]}]
  (let [!result (hooks/use-state nil)]
    (hooks/use-effect (fn [] (reset! !result (eval-string doc))) [doc])
    [:div
     [:div.p-2.bg-slate-100
      [editor (assoc opts :lang :clojure :editable? false)]]
     [:div.viewer-result.mt-1.ml-5
      (when-some [{:keys [error result]} @!result]
        (cond
          error [:div.red error]
          (react/isValidElement result) result
          :else [render/inspect result]))]]))

(def renderers
  (assoc md.transform/default-hiccup-renderers
         :code (fn [_ctx node] [clojure-editor {:doc (md.transform/->text node)}])
         :todo-item (fn [ctx {:as node :keys [attrs]}]
                      (md.transform/into-markup [:li [:input {:type "checkbox" :default-checked (:checked attrs)}]] ctx node))
         :formula (fn [_ctx node]
                    [:span {:dangerouslySetInnerHTML {:__html (.renderToString katex (md.transform/->text node))}}])
         :block-formula (fn [_ctx node]
                          [:div {:dangerouslySetInnerHTML {:__html (.renderToString katex (md.transform/->text node) #js {:displayMode true})}}])))

(defn inspect-expanded [x]
  (r/with-let [expanded-at (r/atom {:hover-path [] :prompt-multi-expand? false})]
    (render/inspect-presented {:!expanded-at expanded-at}
                              (v/present x))))

(defn markdown-editor [_]
  (let [init-text "# 👋 Hello Markdown

```clojure id=xxyyzzww
(reduce + [1 2 3])
```
## Subsection
- [x] type **some**
- [x] ~~nasty~~
- [ ] _stuff_ here"
                                      text->state (fn [text]
                                                    (let [parsed (md/parse text)]
                                                      {:parsed parsed
                                                       :hiccup (nextjournal.markdown.transform/->hiccup renderers parsed)}))
                                      !state (hooks/use-state (text->state init-text))]
                                  [:div.grid.grid-cols-2.m-10
                                   [:div.m-2.p-2.text-xl.border-2.overflow-y-scroll.bg-slate-100 {:style {:height "20rem"}}
                                    [editor {:doc init-text :on-change #(reset! !state (text->state %)) :lang :markdown}]]
                                   [:div.m-2.p-2.font-medium.overflow-y-scroll {:style {:height "20rem"}}
                                    [inspect-expanded (:parsed @!state)]]
                                   [:div.m-2.p-2.overflow-x-scroll
                                    [inspect-expanded (:hiccup @!state)]]
                                   [:div.m-2.p-2.bg-slate-50.viewer-markdown
                                    [v/html (:hiccup @!state)]]]))
