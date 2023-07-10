(ns nextjournal.clerk.render.code
  (:require ["@codemirror/language" :refer [HighlightStyle syntaxHighlighting LanguageDescription]]
            ["@codemirror/state" :refer [Compartment EditorState RangeSet RangeSetBuilder Text]]
            ["@codemirror/view" :refer [EditorView Decoration]]
            ["@lezer/highlight" :refer [tags highlightTree]]
            ["@nextjournal/lang-clojure" :refer [clojureLanguage]]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.clerk.render.hooks :as hooks]
            [nextjournal.clerk.render.localstorage :as localstorage]
            [nextjournal.clojure-mode :as clojure-mode]
            [reagent.core :as r]
            [shadow.esm]))

(def local-storage-dark-mode-key "clerk-darkmode")

(def !dark-mode?
  (r/atom (boolean (localstorage/get-item local-storage-dark-mode-key))))

(defn set-dark-mode! [dark-mode?]
  (let [class-list (.-classList (js/document.querySelector "html"))]
    (if dark-mode?
      (.add class-list "dark")
      (.remove class-list "dark")))
  (localstorage/set-item! local-storage-dark-mode-key dark-mode?))

(defn setup-dark-mode! []
  (add-watch !dark-mode? ::dark-mode-watch
             (fn [_ _ old dark-mode?]
               (when (not= old dark-mode?)
                 (set-dark-mode! dark-mode?))))
  (when @!dark-mode?
    (set-dark-mode! @!dark-mode?)))

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

(j/defn style->hiccup-tag [^js {:keys [tagName class]}]
  (keyword (apply str tagName
                  (when class
                    (cons "." (interpose "." (str/split class #" ")))))))

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
              (if-some [{start :from end :to style :val} (first lds)]
                (recur end
                       (next lds)
                       (concat buf (cond-> (list [(style->hiccup-tag style) (.sliceString text (max from start) (min to end))])
                                     (< pos start)
                                     (conj (.sliceString text pos start)))))
                (cond-> buf
                  (< pos to)
                  (concat [(.sliceString text pos to)]))))))))

(defn import-matching-language-parser [language]
  (.. (shadow.esm/dynamic-import "https://cdn.skypack.dev/@codemirror/language-data@6.1.0")
      (then (fn [^js mod]
              (when-some [langs (.-languages mod)]
                (when-some [^js matching (or (.matchLanguageName LanguageDescription langs language)
                                             (.matchFilename LanguageDescription langs (str "code." language)))]
                  (.load matching)))))
      (then (fn [^js lang-support] (when lang-support (.. lang-support -language -parser))))
      (catch (fn [err] (js/console.warn (str "Cannot load language parser for: " language) err)))))

(defn add-style-ranges! [range-builder syntax-tree]
  (highlightTree syntax-tree highlight-style
                 (fn [from to style]
                   (.add range-builder from to (.mark Decoration (j/obj :class style))))))

(defn clojure-style-rangeset [code]
  (.finish (doto (RangeSetBuilder.)
             (add-style-ranges! (.. ^js clojureLanguage -parser (parse code))))))

(defn syntax-highlight [{:keys [code style-rangeset]}]
  (let [text (.of Text (.split code "\n"))]
    (into [:div.cm-content.whitespace-pre]
          (map (partial style-line style-rangeset text))
          (range 1 (inc (.-lines text))))))

(defn highlight-imported-language [{:keys [code language]}]
  (let [^js builder (RangeSetBuilder.)
        ^js parser (hooks/use-promise (import-matching-language-parser language))]
    (when parser (add-style-ranges! builder (.parse parser code)))
    [syntax-highlight {:code code :style-rangeset (.finish builder)}]))

(defn render-code [^String code {:keys [language]}]
  [:div.cm-editor
   [:cm-scroller
    (cond
      (not language)
      [syntax-highlight {:code code :style-rangeset (.-empty RangeSet)}]
      (#{"clojure" "clojurescript" "clj" "cljs" "cljc" "edn"} language)
      [syntax-highlight {:code code :style-rangeset (clojure-style-rangeset code)}]
      :else
      [highlight-imported-language {:code code :language language}])]])

;; editable code viewer
(defn get-theme []
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
                                                       :border-top-right-radius "3px"}
                  ".cm-tooltip.cm-tooltip-autocomplete" {:border "0"
                                                         :border-radius "6px"
                                                         :box-shadow "0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)"
                                                         "& > ul" {:font-size "12px"
                                                                   :font-family "'Fira Code', monospace"
                                                                   :background "rgb(241 245 249)"
                                                                   :border "1px solid rgb(203 213 225)"
                                                                   :border-radius "6px"}}
                  ".cm-tooltip-autocomplete ul li[aria-selected]" {:background "rgb(79 70 229)"
                                                                   :color "#fff"}
                  ".cm-tooltip.cm-tooltip-hover" {:background "rgb(241 245 249)"
                                                  :border-radius "6px"
                                                  :border "1px solid rgb(203 213 225)"
                                                  :box-shadow "0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)"
                                                  :max-width "550px"}}) #js {:dark @!dark-mode?}))

(def read-only (.. EditorView -editable (of false)))

(defn on-change-ext [f]
  (.. EditorState -transactionExtender
      (of (fn [^js tr]
            (when (.-docChanged tr) (f (.. tr -state sliceDoc)))
            #js {}))))

(def theme (Compartment.))

(defn use-dark-mode [!view]
  (hooks/use-effect (fn []
                      (add-watch !dark-mode? ::dark-mode #(.dispatch @!view #js {:effects (.reconfigure theme (get-theme))}))
                      #(remove-watch !dark-mode? ::dark-mode))))

(def ^:export default-extensions
  #js [clojure-mode/default-extensions
       (syntaxHighlighting highlight-style)
       (.of theme (get-theme))])

(defn make-state [doc extensions]
  (.create EditorState (j/obj :doc doc :extensions extensions)))

(defn make-view [state parent]
  (EditorView. (j/obj :state state :parent parent)))

(defn editor
  ([!code-str] (editor !code-str {}))
  ([!code-str {:keys [extensions on-change]
               :or {on-change #(reset! !code-str %)}}]
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
     (use-dark-mode !view)
     [:div {:ref !container-el}])))
