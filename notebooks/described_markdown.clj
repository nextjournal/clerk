;; # ✍️ Described Markdown
^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns ^:nextjournal.clerk/no-cache described-markdown
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))

;; This notebook contains preparatory work to make markdown node customizable in clerk.

;; Due to the recursive nature of [[v/describe]] it is sufficien to wrap a node at a time:

(defn with-md-viewer [{:as node :keys [type]}]
  (v/wrap-value node (keyword "nextjournal.markdown" (name type))))

;; We define a generic `transform-fn` with 2 purposes:
;; * produces the desired hiccup representation at each node and
;; * delegates to describe the representation of the child nodes:

(defn into-markup [mkup]
  (fn [{:keys [text content]}] (into mkup (if text [text] (map with-md-viewer content)))))

^{::clerk/viewer :hide-result}
(def md-viewers
  [{:name :nextjournal.markdown/doc
    :transform-fn (into-markup [:div.viewer-markdown])
    :fetch-fn v/fetch-all
    :render-fn 'v/html}

   {:name :nextjournal.markdown/heading
    :transform-fn (into-markup [:h1.foo])
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

   {:name :nextjournal.markdown/text
    :transform-fn (into-markup [:span.text])
    :fetch-fn v/fetch-all
    :render-fn 'v/html}

   {:name :nextjournal.markdown/formula
    :fetch-fn #(:text %2)
    :render-fn 'v/katex-viewer}])

^{::clerk/viewers md-viewers}
(with-md-viewer (md/parse "# Hello
This is not _really_ a **strong** formula $\\alpha$."))
