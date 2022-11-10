(ns nextjournal.clerk.render.code
  (:require ["@codemirror/language" :refer [syntaxHighlighting HighlightStyle]]
            ["@lezer/highlight" :refer [tags highlightTree]]
            ["@codemirror/state" :refer [EditorState RangeSetBuilder Text]]
            ["@codemirror/view" :refer [EditorView Decoration]]
            [nextjournal.clerk.render.hooks :as hooks]
            [applied-science.js-interop :as j]
            [nextjournal.clojure-mode :as clojure-mode]))

;; code viewer
(def theme
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

(def highlight-style
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

(def extensions
  #js [clojure-mode/default-extensions
       (syntaxHighlighting highlight-style)
       (.. EditorView -editable (of false))
       theme])

(defn make-state [doc]
  (.create EditorState (j/obj :doc doc :extensions extensions)))

(defn make-view [state parent]
  (EditorView. (j/obj :state state :parent parent)))

(defn rangeset-seq
  "Returns a lazy-seq of ranges inside a RangeSet (like a Decoration set)"
  ([rset] (rangeset-seq rset 0))
  ([^js rset from]
   (let [iterator (.iter rset from)]
     ((fn step []
        (when-some [val (.-value iterator)]
          (let [from (.-from iterator) to (.-to iterator)]
            (.next iterator)
            (cons {:from from :to to :val val}
                  (lazy-seq (step))))))))))

(def ^js syntax (clojure-mode/syntax))

(defn style-markup [^js text {:keys [from to val]}]
  (j/let [^js {:keys [tagName class]} val]
    [(keyword (str tagName "." class)) (.sliceString text from to)]))

(defn style-line [decos ^js text i]
  (j/let [^js {:keys [from to]} (.line text i)]
    (into [:div.cm-line]
          (loop [pos from
                 lds (take-while #(<= (:to %) to) (rangeset-seq decos from))
                 buf ()]
            (if-some [{:as d start :from end :to} (first lds)]
              (recur end
                     (next lds)
                     (concat buf (cond-> (list (style-markup text d))
                                   (< pos start)
                                   (conj (.sliceString text pos start)))))
              (cond-> buf
                (< pos to)
                (concat [(.sliceString text pos to)])))))))

(defn ssr [^String code]
  (let [builder (RangeSetBuilder.)
        _ (highlightTree (.. syntax -parser (parse code)) highlight-style
                         (fn [from to style]
                           (.add builder from to (.mark Decoration (j/obj :class style)))))
        decorations-rangeset (.finish builder)
        text (.of Text (.split code "\n"))]
    [:div.cm-editor
     [:cm-scroller
      (into [:div.cm-content.whitespace-pre]
            (map (partial style-line decorations-rangeset text))
            (range 1 (inc (.-lines text))))]]))

(defn render-code [value]
  (if-not (exists? js/document)
    (ssr value)
    (let [!container-el (hooks/use-ref nil)
          !view (hooks/use-ref nil)]
      (hooks/use-layout-effect (fn [] (let [^js view (reset! !view (make-view (make-state value) @!container-el))]
                                        #(.destroy view))))
      (hooks/use-effect (fn [] (.setState @!view (make-state value))) [value])
      [:div {:ref !container-el}])))
