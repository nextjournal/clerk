(ns nextjournal.clerk.view
  (:require [nextjournal.clerk.viewer :as v]
            [hiccup.page :as hiccup]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.walk :as w]))


(defn ex->viewer [e]
  (v/exception (Throwable->map e)))

#_(doc->viewer (nextjournal.clerk/eval-file "notebooks/elements.clj"))

(defn var->data [v]
  (v/wrapped-with-viewer v))

#_(var->data #'var->data)

(defn fn->str [f]
  (let [pr-rep (pr-str f)
        f-name (subs pr-rep (count "#function[") (- (count pr-rep) 1))]
    f-name))

#_(fn->str (fn []))
#_(fn->str +)

;; TODO: consider removing this and rely only on viewers
(defn make-readable [x]
  (cond-> x
    (var? x) var->data
    (meta x) (with-meta {})
    (fn? x) fn->str))

#_(meta (make-readable ^{:f (fn [])} []))

(defn ->edn [x]
  (binding [*print-namespace-maps* false]
    (pr-str
     (try (w/prewalk make-readable x)
          (catch Throwable _ x)))))

#_(->edn [:vec (with-meta [] {'clojure.core.protocols/datafy (fn [x] x)}) :var #'->edn])

(defn described-result [ns {:keys [result blob-id]}]
  (let [described-result (v/describe result {:viewers (v/get-viewers ns (v/viewers result))})
        metadata (when (v/wrapped-value? described-result)
                   (dissoc described-result :nextjournal/value :nextjournal/viewers))]
    (merge {:nextjournal/viewer :clerk/result
            :nextjournal/value (merge {:blob-id blob-id} metadata)}
           (dissoc metadata :nextjournal/viewer))))

(defn inline-result [ns {:keys [result]}]
  (let [described-result (v/describe result {:viewers (v/get-viewers ns (v/viewers result))})]
    (merge {:nextjournal/viewer :clerk/inline-result
            :nextjournal/value (try {:edn (->edn described-result)}
                                    (catch Throwable _e
                                      {:string (pr-str result)}))}
           (dissoc described-result :nextjournal/value :nextjournal/viewer))))

(defn doc->viewer
  ([doc] (doc->viewer {} doc))
  ([{:keys [inline-results?] :or {inline-results? false}} doc]
   (let [{:keys [ns]} (meta doc)]
     (cond-> (into []
                   (mapcat (fn [{:as x :keys [type text result]}]
                             (case type
                               :markdown [(v/md text)]
                               :code (cond-> [(v/code text)]
                                       (contains? x :result)
                                       (conj (cond
                                               (v/registration? (:result result))
                                               (:result result)

                                               (and (not inline-results?)
                                                    (map? result)
                                                    (contains? result :result)
                                                    (contains? result :blob-id))
                                               (described-result ns result)

                                               :else
                                               (inline-result ns result)))))))
                   doc)
       true v/notebook
       ns (assoc :scope (v/datafy-scope ns))))))

#_(meta (doc->viewer (nextjournal.clerk/eval-file "notebooks/elements.clj")))
#_(nextjournal.clerk/show! "notebooks/test.clj")

(defonce ^{:doc "Load dynamic js from shadow or static bundle from cdn."}
  live-js?
  (when-let [prop (System/getProperty "clerk.live_js")]
    (not= "false" prop)))

(def resource->static-url
  {"/css/app.css" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VxQBDwk3cvr1bt8YVL5m6bJGrFEmzrSbCrH1roypLjJr4AbbteCKh9Y6gQVYexdY85QA2HG5nQFLWpRp69zFSPDJ9"
   "/css/viewer.css" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VxoxUgsBRs2yjjBBcfeCc8XigM7erXHmjJg2tjdGxNBxwTYuDonuYswXqRStaCA2b3rTEPCgPwixJmAVrea1qAHHU"
   "/js/viewer.js" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VweyBqPBoHfLt4gQvos6BmztYqUBoYaHo9ek1WwfQCWQEkzEL1Q4B4GtSe61nx6oL1pJdjyryay5RJTEMYq9j8gLW"})

(defn ->html [{:keys [conn-ws? live-js?] :or {conn-ws? true live-js? live-js?}} doc]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (hiccup/include-css "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css")
    (hiccup/include-css (cond-> "/css/app.css"    (not live-js?) resource->static-url))
    (hiccup/include-css (cond-> "/css/viewer.css" (not live-js?) resource->static-url))
    (hiccup/include-js  (cond-> "/js/viewer.js"   (not live-js?) resource->static-url))]
   [:body
    [:div#clerk]
    [:script "let viewer = nextjournal.clerk.sci_viewer
let doc = " (-> doc ->edn pr-str) "
viewer.reset_doc(viewer.read_string(doc))
viewer.mount(document.getElementById('clerk'))\n"
     (when conn-ws?
       "const ws = new WebSocket(document.location.origin.replace(/^http/, 'ws') + '/_ws')
ws.onmessage = msg => viewer.reset_doc(viewer.read_string(msg.data))
goog.global.ws_send = msg => ws.send(msg)")]]))


(defn ->static-app [{:keys [live-js?] :or {live-js? live-js?}} docs]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (hiccup/include-css "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css")
    (hiccup/include-css (cond-> "/css/app.css"    (not live-js?) resource->static-url))
    (hiccup/include-css (cond-> "/css/viewer.css" (not live-js?) resource->static-url))
    (hiccup/include-js  (cond-> "/js/viewer.js"   (not live-js?) resource->static-url))]
   [:body
    [:div#clerk-static-app]
    [:script "let viewer = nextjournal.clerk.sci_viewer
let app = nextjournal.clerk.static_app
let docs = viewer.read_string(" (-> docs ->edn pr-str) ")
app.init(docs)\n"]]))

(defn doc->html [doc]
  (->html {} (doc->viewer {} doc)))

(defn doc->static-html [doc]
  (->html {:conn-ws? false :live-js? false} (doc->viewer {:inline-results? true} doc)))

#_(let [out "test.html"]
    (spit out (doc->static-html (nextjournal.clerk/eval-file "notebooks/pagination.clj")))
    (clojure.java.browse/browse-url out))
