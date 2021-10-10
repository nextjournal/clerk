(ns nextjournal.clerk.view
  (:require [nextjournal.clerk.viewer :as v]
            [hiccup.page :as hiccup]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.walk :as w]))

(defn described-result [ns {:keys [result blob-id]}]
  (v/with-viewer* :clerk/result
    (-> (v/describe {:viewers (v/get-viewers ns (v/viewers result))} result)
        (assoc :blob-id blob-id))))


#_(v/with-viewers (range 3) [{:pred number? :fn '(fn [x] (v/html [:div.inline-block {:style {:width 16 :height 16}
                                                                                     :class (if (pos? x) "bg-black" "bg-white border-solid border-2 border-black")}]))}])

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
                                       (conj (if (and (not inline-results?)
                                                      (map? result)
                                                      (contains? result :result)
                                                      (contains? result :blob-id)
                                                      (not (v/registration? (:result result))))
                                               (described-result ns result)
                                               (:result result)))))))
                   doc)
       true v/notebook
       ns (assoc :scope (v/datafy-scope ns))))))

#_(meta (doc->viewer (nextjournal.clerk/eval-file "notebooks/elements.clj")))

(defn ex->viewer [e]
  (v/exception (Throwable->map e)))

#_(doc->viewer (nextjournal.clerk/eval-file "notebooks/elements.clj"))

(defn var->data [v]
  (v/with-viewer* :clerk/var (symbol v)))

#_(var->data #'var->data)

(defn fn->data [f]
  (let [pr-rep (pr-str f)
        f-name (subs pr-rep (count "#function[") (- (count pr-rep) 1))]
    (v/with-viewer* :fn f-name)))

#_(fn->data (fn []))
#_(fn->data +)

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
  (when-let [prop (System/getProperty "clerk.live_js")]
    (not= "false" prop)))

(def resource->static-url
  {"/css/app.css" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VxQBDwk3cvr1bt8YVL5m6bJGrFEmzrSbCrH1roypLjJr4AbbteCKh9Y6gQVYexdY85QA2HG5nQFLWpRp69zFSPDJ9"
   "/css/viewer.css" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VxoxUgsBRs2yjjBBcfeCc8XigM7erXHmjJg2tjdGxNBxwTYuDonuYswXqRStaCA2b3rTEPCgPwixJmAVrea1qAHHU"
   "/js/viewer.js" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VwLM6VpBBWfsaLV4hXZtRTGa2hAzRH2XcucRGgb9Ycqq9vLBq7cfWr1uAirVkGxJUodPfjEHoepuFPvgBcZdkAoND"})

(defn ->html [{:keys [conn-ws? live-js?] :or {conn-ws? true live-js? live-js?}} doc]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    (hiccup/include-css "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css")
    (hiccup/include-css (cond-> "/css/app.css"    (not live-js?) resource->static-url))
    (hiccup/include-css (cond-> "/css/viewer.css" (not live-js?) resource->static-url))
    (hiccup/include-js  (cond-> "/js/viewer.js"   (not live-js?) resource->static-url))]
   [:body
    [:div#clerk]
    [:script "let viewer = nextjournal.clerk.sci_viewer
let doc = " (with-out-str (-> doc ->edn pprint/pprint)) "
viewer.reset_doc(viewer.read_string(doc))
viewer.mount(document.getElementById('clerk'))\n"
     (when conn-ws?
       "const ws = new WebSocket(document.location.origin.replace(/^http/, 'ws') + '/_ws')
ws.onmessage = msg => viewer.reset_doc(viewer.read_string(msg.data))")]]))


(defn ->static-app [{:keys [live-js?] :or {live-js? live-js?}} docs]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
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
