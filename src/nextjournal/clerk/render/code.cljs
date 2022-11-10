(ns nextjournal.clerk.render.code
  (:require ["@codemirror/language" :refer [syntaxHighlighting HighlightStyle]]
            ["@lezer/highlight" :refer [tags]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/view" :refer [EditorView]]
            [nextjournal.clerk.render.hooks :as hooks]
            [applied-science.js-interop :as j]
            [nextjournal.clojure-mode :as clojure-mode]))

;; code viewer
(def cm-theme
  (.theme EditorView
          (j/lit {"&.cm-focused" {:outline "none"}
                  ".cm-line" {:padding "0"
                              :line-height "1.6"
                              :font-size "15px"
                              :font-family "\"Fira Mono\", monospace"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}

                  ;; only show cursor when focused
                  ".cm-cursor" {:visibility "hidden"}
                  "&.cm-focused .cm-cursor" {:visibility "visible"
                                             :animation "steps(1) cm-blink 1.2s infinite"}
                  "&.cm-focused .cm-selectionBackground" {:background-color "Highlight"}
                  ".cm-tooltip" {:border "1px solid rgba(0,0,0,.1)"
                                 :border-radius "3px"
                                 :overflow "hidden"}
                  ".cm-tooltip > ul > li" {:padding "3px 10px 3px 0 !important"}
                  ".cm-tooltip > ul > li:first-child" {:border-top-left-radius "3px"
                                                       :border-top-right-radius "3px"}})))

(def cm-highlight-style
  (.define HighlightStyle
           (clj->js [{:tag (.-meta tags) :class "cmt-meta"}
                     {:tag (.-link tags) :class "cmt-link"}
                     {:tag (.-heading tags) :class "cmt-heading"}
                     {:tag (.-emphasis tags) :class "cmt-italic"}
                     {:tag (.-strong tags) :class "cmt-strong"}
                     {:tag (.-strikethrough tags) :class "cmt-strikethrough"}
                     {:tag (.-keyword tags) :class "cmt-keyword"}
                     {:tag (.-atom tags) :class "cmt-atom"}
                     {:tag (.-bool tags) :class "cmt-bool"}
                     {:tag (.-url tags) :class "cmt-url"}
                     {:tag (.-contentSeparator tags) :class "cmt-contentSeparator"}
                     {:tag (.-labelName tags) :class "cmt-labelName"}
                     {:tag (.-literal tags) :class "cmt-literal"}
                     {:tag (.-inserted tags) :class "cmt-inserted"}
                     {:tag (.-string tags) :class "cmt-string"}
                     {:tag (.-deleted tags) :class "cmt-deleted"}
                     {:tag (.-regexp tags) :class "cmt-regexp"}
                     {:tag (.-escape tags) :class "cmt-escape"}
                     {:tag (.. tags (special (.-string tags))) :class "cmt-string"}
                     {:tag (.. tags (definition (.-variableName tags))) :class "cmt-variableName"}
                     {:tag (.. tags (local (.-variableName tags))) :class "cmt-variableName"}
                     {:tag (.-typeName tags) :class "cmt-typeName"}
                     {:tag (.-namespace tags) :class "cmt-namespace"}
                     {:tag (.-className tags) :class "cmt-className"}
                     {:tag (.. tags (special (.-variableName tags))) :class "cmt-variableName"}
                     {:tag (.-macroName tags) :class "cmt-macroName"}
                     {:tag (.. tags (definition (.-propertyName tags))) :class "cmt-propertyName"}
                     {:tag (.-comment tags) :class "cmt-comment"}
                     {:tag (.-invalid tags) :class "cmt-invalid"}])))

(def cm-extensions
  #js [clojure-mode/default-extensions
       (syntaxHighlighting cm-highlight-style)
       (.. EditorView -editable (of false))
       cm-theme])

(defn cm-state [doc] (.create EditorState (j/obj :doc doc :extensions cm-extensions)))
(defn cm-view [state parent] (EditorView. (j/obj :state state :parent parent)))

(defn render-code [value]
  (let [!container (hooks/use-ref nil)
        !view (hooks/use-ref nil)
        !state (hooks/use-ref (cm-state value))]
    (hooks/use-effect (fn []
                        (js/console.log :create-view value)
                        (let [^js view (reset! !view (cm-view @!state @!container))]
                          #(do (js/console.log :destroy-view value) (.destroy view)))) [])
    (hooks/use-effect (fn []
                        (js/console.log :reset-state value)
                        (cond->> (reset! !state (cm-state value))
                          @!view (.setState @!view))) [value])
    [:div {:ref !container}]))
