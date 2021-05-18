(ns nextjournal.clerk.view
  (:require [nextjournal.viewer :as v]
            [hiccup.page :as hiccup]))

(defn doc->viewer [doc]
  (into ^{:nextjournal/viewer :notebook} []
        (mapcat (fn [{:keys [type text result]}]
                  (case type
                    :markdown [(v/view-as :markdown text)]
                    :code (cond-> [(v/view-as :code text)]
                            result (conj result)))))
        doc))

(defn ex->viewer [e]
  (into ^{:nextjournal/viewer :notebook}
        [(v/view-as :code (pr-str (Throwable->map e)))]))


#_(doc->viewer (nextjournal.clerk/eval-file "notebooks/elements.clj"))

(defn ->edn [x]
  (binding [*print-meta* true
            *print-namespace-maps* false]
    (pr-str x)))

#_(->edn (let [file "notebooks/elements.clj"]
           (doc->viewer (hashing/hash file) (hashing/parse-file {:markdown? true} file))))

(def live-js?
  "Load dynamic js from shadow or static bundle from cdn."
  true)

(defn ->html [viewer]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    (hiccup/include-css "https://cdn.jsdelivr.net/gh/tonsky/FiraCode@5.2/distr/fira_code.css")
    (hiccup/include-css
     (if live-js?
       "https://cdn.dev.nextjournal.com:8888/build/stylesheets/nextjournal.css"
       "https://cdn.nextjournal.com/data/QmT83tykSqNhth4cSkuo8j9Z6mkWTJDGJ7FuaZTwBYQaNP?filename=nextjournal.css&content-type=text/css"))
    (hiccup/include-css
     (if live-js?
       "https://cdn.dev.nextjournal.com:8888/build/stylesheets/viewer.css"
       "https://cdn.nextjournal.com/data/QmPKsreb65rQu51tuxQkLjkKGp3DxuFHPUbmUV1rehMA21?filename=viewer.css&content-type=text/css"))

    (hiccup/include-js
     (if live-js?
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
