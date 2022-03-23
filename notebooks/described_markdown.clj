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

;; We define a generic `fetch-fn` to drill down into the markdown structure. At each depth this produces the desired hiccup representation of the node and delegates to describe the representation of the children nodes:

(defn into-markup [mkup]
  (fn [opts {:keys [text content]}]
    (v/fetch-all opts (into mkup (if text [text] (map with-md-viewer content))))))

^{::clerk/viewer :hide-result}
(def md-viewers
  [{:name :nextjournal.markdown/doc
    :fetch-fn (into-markup [:div.viewer-markdown])
    :render-fn 'v/html}

   {:name :nextjournal.markdown/heading
    :fetch-fn (into-markup [:h1.foo])
    :render-fn 'v/html}

   {:name :nextjournal.markdown/paragraph
    :fetch-fn (into-markup [:p])
    :render-fn 'v/html}

   {:name :nextjournal.markdown/em
    :fetch-fn (into-markup [:em])
    :render-fn 'v/html}

   {:name :nextjournal.markdown/strong
    :fetch-fn (into-markup [:strong])
    :render-fn 'v/html}

   {:name :nextjournal.markdown/text
    :fetch-fn (into-markup [:span.text])
    :render-fn 'v/html}

   {:name :nextjournal.markdown/formula
    :fetch-fn #(:text %2)
    :render-fn 'v/katex-viewer}])

^{::clerk/viewers md-viewers}
(with-md-viewer (md/parse "# Hello
This is _really_ a **strong** formula $\\alpha$."))
