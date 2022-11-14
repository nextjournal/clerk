(ns nextjournal.clerk.render.code
  (:require ["@codemirror/language" :refer [HighlightStyle]]
            ["@lezer/highlight" :refer [tags highlightTree]]
            ["@codemirror/state" :refer [RangeSetBuilder Text]]
            ["@codemirror/view" :refer [Decoration]]
            ["@nextjournal/lang-clojure" :refer [clojureLanguage]]
            [applied-science.js-interop :as j]
            [clojure.string :as str]))

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

(defn style-markup [^js text {:keys [from to val]}]
  (j/let [^js {:keys [tagName class]} val]
    [(keyword (apply str tagName (when class
                                   (cons "." (interpose "." (str/split class #" "))))))
     (.sliceString text from to)]))

;; NOTE: these styles are partially overlapping with those for the `.viewer-code` container
;; but are needed to fix rendered code _outside_ of it. e.g. in Clerk results
(def line-styles {:padding "0"
                  :line-height "1.6"
                  :font-size "15px"
                  :font-family "\"Fira Mono\", monospace"})

(defn style-line [decos ^js text i]
  (j/let [^js {:keys [from to]} (.line text i)]
    (into [:div.cm-line {:style line-styles}]
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
