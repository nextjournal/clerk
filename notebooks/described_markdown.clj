;; # ✍️ Described Markdown
^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns ^:nextjournal.clerk/no-cache described-markdown
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.parser :as md.parser]
            [nextjournal.markdown.transform :as md.transform]))

^{::clerk/viewer :hide-result}
(defn parse [text]
  (md.parser/parse (update md.parser/empty-doc :text-tokenizers conj
                           {:regexp #"\{\{([^{]+)\}\}"
                            :handler (fn [m] {:type :inline :text (m 1)})})
                   (md/tokenize text)))

;; This notebook contains preparatory work to make markdown node customizable in clerk.

;; Due to the recursive nature of [[v/describe]] it is sufficien to wrap a node at a time:

(defn with-md-viewer [{:as node :keys [type]}]
  (v/wrap-value node (keyword "nextjournal.markdown" (name type))))

;; We define a generic `transform-fn` with 2 purposes:
;; * produces the desired hiccup representation at each node and
;; * delegates to describe the representation of the child nodes:

(defn into-markup [mkup]
  (let [mkup-fn (if (fn? mkup) mkup (constantly mkup))]
    (fn [{:as node :keys [text content]}]
      (into (mkup-fn node) (if text [text] (map with-md-viewer content))))))

(defn red [text] (v/html [:span {:style {:color "#ef4444"}} text]))

^{::clerk/viewer :hide-result}
(def md-viewers
  [{:name :nextjournal.markdown/doc
    :transform-fn (into-markup [:div.viewer-markdown])
    :fetch-fn v/fetch-all
    :render-fn 'v/html}

   {:name :nextjournal.markdown/heading
    :transform-fn (into-markup #(vector (str "h" (:heading-level %))))
    :fetch-fn v/fetch-all
    :render-fn 'v/html}

   {:name :nextjournal.markdown/paragraph
    :transform-fn (into-markup [:p])
    :fetch-fn v/fetch-all
    :render-fn 'v/html}

   {:name :nextjournal.markdown/em
    :transform-fn (into-markup [:em])
    :fetch-fn v/fetch-all
    :render-fn 'v/html}

   {:name :nextjournal.markdown/strong
    :transform-fn (into-markup [:strong])
    :fetch-fn v/fetch-all
    :render-fn 'v/html}

   {:name :nextjournal.markdown/internal-link
    :transform-fn (into-markup #(vector :a {:href (str "#" (:text %))}))
    :fetch-fn v/fetch-all
    :render-fn 'v/html}

   {:name :nextjournal.markdown/text
    ;; TODO: find a way to drop wrapping [:span]
    :transform-fn (into-markup [:span.text])
    :fetch-fn v/fetch-all
    :render-fn 'v/html}

   {:name :nextjournal.markdown/inline
    ;; TODO: use clerk/read+eval-cached
    :transform-fn (comp eval read-string :text)
    :fetch-fn v/fetch-all
    :render-fn 'v/html}

   {:name :nextjournal.markdown/formula
    :fetch-fn #(:text %2)
    :render-fn 'v/katex-viewer}])

(def text "# Hello

This is not _really_ a **strong** formula $\\alpha$.

## Section 3

with inline wiki [[link]] and inline eval {{ (red \"ahoi\") }}
")

^{::clerk/viewers md-viewers}
(with-md-viewer (parse text))

^{::clerk/visibility :hide}
(comment
  (v/describe
   (v/with-viewers md-viewers
     (with-md-viewer (parse text)))
   ))
