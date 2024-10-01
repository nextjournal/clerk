(ns nextjournal.clerk.markdown-editor
  (:require
   ["katex" :as katex]
   [nextjournal.markdown :as md]
   [nextjournal.clerk.viewer :as v]
   [nextjournal.clerk.render.hooks :as hooks]
   [nextjournal.markdown.transform :as md.transform]))

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
          'else [render/inspect result]))]]))

(def renderers
  (assoc md.transform/default-hiccup-renderers
         :code (fn [_ctx node] [clojure-editor {:doc (md.transform/->text node)}])
         :todo-item (fn [ctx {:as node :keys [attrs]}]
                      (md.transform/into-markup [:li [:input {:type "checkbox" :default-checked (:checked attrs)}]] ctx node))
         :formula (fn [_ctx node]
                    [:span {:dangerouslySetInnerHTML {:__html (.renderToString katex (md.transform/->text node))}}])
         :block-formula (fn [_ctx node]
                          [:div {:dangerouslySetInnerHTML {:__html (.renderToString katex (md.transform/->text node) #js {:displayMode true})}}])))

(defn editor [_]
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
                                    [v/html (:hiccup @!state)]]])
  )
