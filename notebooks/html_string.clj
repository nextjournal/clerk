;; # HTML String Rendering

;; This is an experiment to find out what's required to support
;; rendering to HTML strings via hiccup, using an alternative viewer
;; stack. If this is feasible, it could be the basis for a much
;; smaller client-side bundle in combination with a morphing lib while
;; moving some of the viewers implementation to custom elements.

(ns html-string
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as viewer]
            [clojure.repl.deps :as deps]))

(defn render-cell [xs]
  (apply list xs))

(defn render-notebook [{xs :blocks :keys [package doc-css-class sidenotes? toc toc-visibility header footer]}
                       #_{:as render-opts :keys [!expanded-at]}]
  [:div.flex
   [:div.flex-auto.w-screen.scroll-container
    (into
     [:div
      (merge
       {#_#_:key "notebook-viewer"
        :class (cond-> (or doc-css-class (mapv name [:flex :flex-col :items-center :notebook-viewer :flex-auto]))
                 sidenotes? (conj :sidenotes-layout))})]
     (concat (when header [header]) xs (when footer [footer])))]])

(defn render-html [html]
  html)

(defn render-code-block [code-string #_{:as opts :keys [id]}]
  [:div.viewer.code-viewer.w-full.max-w-wide #_{:data-block-id id}
   code-string
   #_
   [code/render-code code-string (assoc opts :language "clojure")]])

(defn render-toc [_])

(def modified-viewers
  [(assoc viewer/cell-viewer :render-fn `render-cell)
   (assoc viewer/notebook-viewer :render-fn `render-notebook)
   (assoc viewer/code-block-viewer :render-fn `render-code-block)
   (assoc viewer/html-viewer :render-fn `render-html)
   (assoc viewer/toc-viewer :render-fn `render-toc)])

(defn doc->viewer [doc]
  (viewer/present
   (viewer/with-viewers (viewer/add-viewers modified-viewers)
     (viewer/notebook doc))))

(def viewer-doc
  (doc->viewer (eval/eval-file "notebooks/hello.clj")))

(defn extract-viewers [viewer-doc]
  (into []
        (keep :nextjournal/viewer)
        (tree-seq coll? seq viewer-doc)))

(sort (keys (frequencies (mapv (comp :form :render-fn) (extract-viewers viewer-doc)))))

(defn ->fn [sym]
  (cond
    (simple-symbol? sym) (ns-resolve 'clojure.core sym)
    (qualified-symbol? sym) (requiring-resolve sym)
    :else (throw (ex-info "->fn expected symbol, got" {:sym sym}))))

(defn eval-viewers [viewer-doc]
  (clojure.walk/postwalk (fn [x]
                           (if (and (map? x)
                                    (contains? x :nextjournal/viewer)
                                    (contains? x :nextjournal/value))
                             ((->fn (get-in x [:nextjournal/viewer :render-fn :form]))
                              (:nextjournal/value x))
                             x))
                         viewer-doc))
#_
(clerk/present! (viewer/html
                 (eval-viewers viewer-doc)))

(defonce html
  (do
    (deps/add-lib 'io.github.escherize/huff)
    @(requiring-resolve 'huff2.core/html)))

(defn ->html [viewer-doc]
  (nextjournal.clerk.view/->html {:exclude-js? true
                                  :html (html (eval-viewers viewer-doc))}))

(doto "test.html"
  (spit (->html viewer-doc))
  (clojure.java.browse/browse-url))



