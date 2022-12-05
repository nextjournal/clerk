(ns nextjournal.clerk.render.code
  (:require ["@codemirror/language" :refer [HighlightStyle syntaxHighlighting]]
            ["@lezer/highlight" :refer [tags highlightTree]]
            ["@codemirror/state" :refer [EditorState RangeSetBuilder Text]]
            ["@codemirror/view" :refer [EditorView Decoration keymap]]
            ["@nextjournal/lang-clojure" :refer [clojureLanguage]]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.clerk.render.hooks :as hooks]
            [nextjournal.clojure-mode :as clojure-mode]
            [nextjournal.clojure-mode.keymap :as clojure-mode.keymap]))

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

(defn style-markup [text style]
  (j/let [^js {:keys [tagName class]} style]
    [(keyword (apply str tagName (when class
                                   (cons "." (interpose "." (str/split class #" "))))))
     text]))

(j/defn intersects? [^js {:keys [from to]} range]
  (or
   (and (<= from (:from range)) (< (:from range) to))
   (and (< from (:to range)) (<= (:to range) to))
   (and (<= (:from range) from) (<= to (:to range)))))

(defn style-line [decos ^js text i]
  (j/let [^js {:as line :keys [from to length]} (.line text i)]
    ;; NOTE: these styles are partially overlapping with those for the `.viewer-code` container
    ;; but are needed to fix rendered code _outside_ of it. e.g. in Clerk results
    (into [:div.cm-line {:style {:padding "0"
                                 :line-height "1.6"
                                 :font-size "15px"
                                 :font-family "\"Fira Mono\", monospace"}}]
          (if (zero? length)
            "\n"
            (loop [pos from
                   lds (filter (partial intersects? line) (rangeset-seq decos))
                   buf ()]
              (if-some [{:as d start :from end :to style :val} (first lds)]
                (recur end
                       (next lds)
                       (concat buf (cond-> (list (style-markup (.sliceString text (max from start) (min to end)) style))
                                     (< pos start)
                                     (conj (.sliceString text pos start)))))
                (cond-> buf
                  (< pos to)
                  (concat [(.sliceString text pos to)]))))))))


(defn render-code [^String code]
  (let [builder (RangeSetBuilder.)
        _ (highlightTree (.. clojureLanguage -parser (parse code)) highlight-style
                         (fn [from to style]
                           (.add builder from to (.mark Decoration (j/obj :class style)))))
        decorations-rangeset (.finish builder)
        text (.of Text (.split code "\n"))]
    [:div.cm-editor
     [:cm-scroller
      (into [:div.cm-content.whitespace-pre]
            (map (partial style-line decorations-rangeset text))
            (range 1 (inc (.-lines text))))]]))

;; editable code viewer
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

(def ^js complete-keymap (.of keymap clojure-mode.keymap/complete))
(def ^js builtin-keymap (.of keymap clojure-mode.keymap/builtin))
(def ^js paredit-keymap (.of keymap clojure-mode.keymap/paredit))

(def read-only (.. EditorView -editable (of false)))

(defn on-change-ext [f]
  (.. EditorState -transactionExtender
      (of (fn [^js tr]
            (when (.-docChanged tr) (f (.. tr -state sliceDoc)))
            #js {}))))

(def ^:export default-extensions
  #js [clojure-mode/default-extensions
       (syntaxHighlighting highlight-style)
       theme])

(defn make-state [doc extensions]
  (.create EditorState (j/obj :doc doc :extensions extensions)))

(defn make-view [state parent]
  (EditorView. (j/obj :state state :parent parent)))

(defn editor
  ([!code-str] (editor !code-str {}))
  ([!code-str {:keys [extensions on-change]}]
   (let [!container-el (hooks/use-ref nil)
         !view (hooks/use-ref nil)]
     ;; view instance is built only once
     (hooks/use-effect
      (fn []
        (let [^js view
              (reset! !view (make-view (make-state @!code-str
                                                   (cond-> default-extensions
                                                     (seq extensions) (.concat extensions)
                                                     on-change (.concat (on-change-ext on-change)))) @!container-el))]
          #(.destroy view))))
     (hooks/use-effect
      (fn []
        (let [^js state (.-state @!view)]
          (when (not= @!code-str (.sliceDoc state))
            (.dispatch @!view
                       (.update state
                                (j/lit {:changes [{:insert @!code-str
                                                   :from 0 :to (.. state -doc -length)}]}))))))
      [@!code-str])
     [:div {:ref !container-el}])))
