(ns nextjournal.clerk.view
  (:require [nextjournal.viewer :as v]
            [hiccup.page :as hiccup]))

(defn doc->viewer [doc]
  (into ^{:nextjournal/viewer :flex-col} []
        (mapcat (fn [{:keys [type text result]}]
                  (case type
                    :markdown [(v/view-as :markdown text)]
                    :code (cond-> [(v/view-as :code text)]
                            result (conj result)))))
        doc))

#_(doc->viewer (nextjournal.clerk/eval-file "notebooks/elements.clj"))

(defn ->edn [x]
  (binding [*print-meta* true
            *print-namespace-maps* false]
    (pr-str x)))

#_(->edn (let [file "notebooks/elements.clj"]
           (doc->viewer (hashing/hash file) (hashing/parse-file {:markdown? true} file))))

(def live-js?
  "Load dynamic js from shadow or static bundle from cdn."
  false)

(defn ->html [viewer]
  (hiccup/html5
   [:head
    [:style
     ".cm-editor { background-color: #eee; }"]
    [:meta {:charset "UTF-8"}]
    (hiccup/include-css "https://cdn.jsdelivr.net/gh/tonsky/FiraCode@5.2/distr/fira_code.css")
    (hiccup/include-css "https://cdn.nextjournal.com/data/QmRqugy58UfVG5j9Lo2ccKpkV6tJ2pDDfrZXViRApUKG4v?filename=viewer-a098a51e8ec9999fae7673b325889dbccafad583.css&content-type=text/css")
    (hiccup/include-js (if live-js?
                         "/js/out/viewer.js"
                         "https://cdn.nextjournal.com/data/Qmeo8gbH53vBr3r47JBdYbwKwboWQCDKui5pfsEgxvgEpy?filename=viewer.js&content-type=application/x-javascript"))]
   [:body
    [:div#app]
    [:script "nextjournal.viewer.notebook.mount(document.getElementById('app'))
nextjournal.viewer.notebook.reset_state(nextjournal.viewer.notebook.read_string(" (-> viewer ->edn pr-str) "))"]
    [:script "const ws = new WebSocket('ws://localhost:7777/_ws')
ws.onmessage = msg => nextjournal.viewer.notebook.reset_state(nextjournal.viewer.notebook.read_string(msg.data))"]]))


(defn doc->html
  [doc]
  (->html (doc->viewer doc)))

#_(doc->html (nextjournal.clerk/eval-file "notebooks/elements.clj"))

#_(let [doc (nextjournal.clerk/eval-file "notebooks/elements.clj")
        out "test.html"]
    (spit out (->html doc))
    (clojure.java.browse/browse-url out))
