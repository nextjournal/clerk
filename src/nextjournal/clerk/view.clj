(ns nextjournal.clerk.view
  (:require [nextjournal.viewer :as v]
            [hiccup.page :as hiccup]
            [clojure.walk :as w]))

(defn doc->viewer [doc]
  (into (v/view-as :clerk/notebook [])
        (mapcat (fn [{:as x :keys [type text result]}]
                  (case type
                    :markdown [(v/view-as :markdown text)]
                    :code (cond-> [(v/view-as :code text)]
                            (contains? x :result)
                            (conj (if (and (instance? clojure.lang.IMeta result)
                                           (contains? (meta result) :blob/id))
                                    (v/view-as :clerk/blob (v/describe result))
                                    result))))))
        doc))

(defn ex->viewer [e]
  (into ^{:nextjournal/viewer :notebook}
        [(v/view-as :code (pr-str (Throwable->map e)))]))


#_(doc->viewer (nextjournal.clerk/eval-file "notebooks/elements.clj"))

(defn var->data [v]
  (v/view-as :clerk/var (symbol v)))

#_(var->data #'var->data)

(defn datafy-vars [x]
  (let [m (meta x)]
    (cond-> (w/prewalk #(cond-> % (var? %) var->data) x)
      m (with-meta m))))

#_(datafy-vars [{:x #'var->data}])

(defn ->edn [x]
  (binding [*print-meta* true
            *print-namespace-maps* false]
    (pr-str (datafy-vars x))))

#_(->edn {:foo #'->edn})

(defonce ^{:doc "Load dynamic js from shadow or static bundle from cdn."}
  live-js?
  false)


(defn ->html [viewer]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    (hiccup/include-css
     (if live-js?
       "https://cdn.dev.nextjournal.com:8888/build/stylesheets/viewer.css"
       "https://cdn-beta.nextjournal.com/data/QmZc2Rhp7GqGAuZFp8SH6yVLLh3sz3cyjDwZtR5Q8PGcye?filename=viewer-a098a51e8ec9999fae7673b325889dbccafad583.css&content-type=text/css"))

    (hiccup/include-js
     (if live-js?
       "/js/out/viewer.js"
       "https://cdn.nextjournal.com/data/QmTRQtJzUyqsFmTSgQCGgHQ3F8UTowBiZxZrharffJemb5?filename=viewer.js&content-type=application/x-javascript"))]
   [:body
    [:div#app]
    [:script "nextjournal.viewer.notebook.mount(document.getElementById('app'))
nextjournal.viewer.notebook.reset_state(nextjournal.viewer.notebook.read_string(" (-> viewer ->edn pr-str) "))"]
    [:script "const ws = new WebSocket(document.location.origin.replace(/^http/, 'ws') + '/_ws')
ws.onmessage = msg => nextjournal.viewer.notebook.reset_state(nextjournal.viewer.notebook.read_string(msg.data))"]]))


(defn doc->html
  [doc]
  (->html (doc->viewer doc)))

#_(doc->html (nextjournal.clerk/eval-file "notebooks/elements.clj"))

#_(let [doc (nextjournal.clerk/eval-file "notebooks/elements.clj")
        out "test.html"]
    (spit out (->html doc))
    (clojure.java.browse/browse-url out))
