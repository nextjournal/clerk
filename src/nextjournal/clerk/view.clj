(ns nextjournal.clerk.view
  (:require [nextjournal.clerk.viewer :as v]
            [hiccup.page :as hiccup]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.walk :as w]))

(defn described-result [ns result]
  (-> (v/describe {:viewers (v/get-viewers ns (v/viewers result))} result)
      (assoc :blob-id (:nextjournal/blob-id result))
      (v/with-viewer :clerk/result)))


#_(v/with-viewers (range 3) [{:pred number? :fn '(fn [x] (v/html [:div.inline-block {:style {:width 16 :height 16}
                                                                                     :class (if (pos? x) "bg-black" "bg-white border-solid border-2 border-black")}]))}])

(defn doc->viewer
  ([doc] (doc->viewer {} doc))
  ([{:keys [inline-results?] :or {inline-results? false}} doc]
   (let [{:keys [ns]} (meta doc)]
     (-> (into []
               (mapcat (fn [{:as x :keys [type text result]}]
                         (case type
                           :markdown [(v/view-as :markdown text)]
                           :code (cond-> [(v/view-as :code text)]
                                   (contains? x :result)
                                   (conj (if (and (not inline-results?)
                                                  (v/wrapped-value? result)
                                                  (contains? result :nextjournal/blob-id)
                                                  (not (v/registration? result)))
                                           (described-result ns result)
                                           result))))))
               doc)
         (v/with-viewer :clerk/notebook)
         (assoc :scope (v/datafy-scope ns))))))

#_(meta (doc->viewer (nextjournal.clerk/eval-file "notebooks/elements.clj")))

(defn ex->viewer [e]
  (into ^{:nextjournal/viewer :notebook}
        [(v/view-as :code (pr-str (Throwable->map e)))]))

#_(doc->viewer (nextjournal.clerk/eval-file "notebooks/elements.clj"))

(defn var->data [v]
  (v/view-as :clerk/var (symbol v)))

#_(var->data #'var->data)

(defn fn->data [_]
  {:nextjournal/value 'fn :nextjournal/type-key :fn})

#_(fn->data (fn []))

(defn make-printable [x]
  (cond-> x
    (var? x) var->data
    (meta x) (vary-meta #(w/prewalk make-printable %))
    (fn? x) fn->data))

#_(meta (make-printable ^{:f (fn [])} []))

(defn ->edn [x]
  (binding [#_#_*print-meta* true
            *print-namespace-maps* false]
    (pr-str (w/prewalk make-printable x))))

#_(->edn [:vec (with-meta [] {'clojure.core.protocols/datafy (fn [x] x)}) :var #'->edn])

(defonce ^{:doc "Load dynamic js from shadow or static bundle from cdn."}
  live-js?
  true)


(defn ->html [{:keys [conn-ws?] :or {conn-ws? true}} doc]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    (hiccup/include-css
     "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css")
    (hiccup/include-css
     (if live-js?
       "/css/app.css"
       "TODO"))
    (hiccup/include-css
     (if live-js?
       "/css/viewer.css"
       "https://cdn.nextjournal.com/data/QmZc2Rhp7GqGAuZFp8SH6yVLLh3sz3cyjDwZtR5Q8PGcye?filename=viewer-a098a51e8ec9999fae7673b325889dbccafad583.css&content-type=text/css"))
    (hiccup/include-js
     (if live-js?
       "/js/viewer.js"
       "https://cdn.nextjournal.com/data/Qmc5rjhjB6irjrJnCgsB4JU3Vvict3DEHeV4Zvq7GJQv4F?filename=viewer.js&content-type=application/x-javascript"))]
   [:body
    [:div#clerk]
    [:script "let viewer = nextjournal.clerk.sci_viewer
let doc = " (with-out-str (-> doc ->edn pprint/pprint)) "
viewer.reset_doc(viewer.read_string(doc))
viewer.mount(document.getElementById('clerk'))\n"
     (when conn-ws?
       "const ws = new WebSocket(document.location.origin.replace(/^http/, 'ws') + '/_ws')
ws.onmessage = msg => viewer.reset_doc(viewer.read_string(msg.data))")]]))


(defn doc->html [doc]
  (->html {} (doc->viewer {} doc)))

(defn doc->static-html [doc]
  (->html {:conn-ws? false} (doc->viewer {:inline-results? true} doc)))

#_(let [out "test.html"]
    (spit out (doc->static-html (nextjournal.clerk/eval-file "notebooks/pagination.clj")))
    (clojure.java.browse/browse-url out))
