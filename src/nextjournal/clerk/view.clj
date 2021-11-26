(ns nextjournal.clerk.view
  (:require [nextjournal.clerk.config :as config]
            [nextjournal.clerk.viewer :as v]
            [hiccup.page :as hiccup]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.walk :as w]
            [valuehash.api :as valuehash]))


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

(defn ->hash-str
  "Attempts to compute a hash of `value` falling back to a random string."
  [value]
  (try
    (let [limit config/*bounded-count-limit*]
      (when (and (seqable? value)
                 (= limit (bounded-count limit value)))
        (throw (ex-info "not countable within limit" {:limit limit}))))
    (valuehash/sha-1-str value)
    (catch Exception _e
      (str (gensym)))))

#_(->hash (range 104))

(defn ->result [ns {:nextjournal/keys [value blob-id visibility]} lazy-load?]
  (let [described-result (v/describe value {:viewers (v/get-viewers ns (v/viewers value))})]
    (merge {:nextjournal/viewer :clerk/result
            :nextjournal/value (cond-> (try {:nextjournal/edn (->edn described-result)}
                                            (catch Throwable _e
                                              {:nextjournal/string (pr-str value)}))
                                 lazy-load?
                                 (assoc :nextjournal/fetch-opts {:blob-id blob-id}
                                        :nextjournal/hash (->hash-str [blob-id described-result])))}

           (dissoc described-result :nextjournal/value :nextjournal/viewer))))

#_(nextjournal.clerk/show! "notebooks/hello.clj")

(defn ->display [{:as code-cell :keys [result ns?]}]
  (let [{:nextjournal.clerk/keys [visibility]} result
        result? (and (contains? code-cell :result)
                     (not= :hide-result (v/viewer (v/value result)))
                     (not (contains? visibility :hide-ns))
                     (not (and ns? (contains? visibility :hide))))
        fold? (and (not (contains? visibility :hide-ns))
                   (or (contains? visibility :fold)
                       (contains? visibility :fold-ns)))
        code? (or fold? (contains? visibility :show))]
    {:result? result? :fold? fold? :code? code?}))

#_(->display {:result {:nextjournal.clerk/visibility #{:fold :hide-ns}}})
#_(->display {:result {:nextjournal.clerk/visibility #{:fold-ns}}})
#_(->display {:result {:nextjournal.clerk/visibility #{:hide}} :ns? false})
#_(->display {:result {:nextjournal.clerk/visibility #{:fold}} :ns? true})
#_(->display {:result {:nextjournal.clerk/visibility #{:fold}} :ns? false})
#_(->display {:result {:nextjournal.clerk/visibility #{:hide} :nextjournal/value {:nextjournal/viewer :hide-result}} :ns? false})
#_(->display {:result {:nextjournal.clerk/visibility #{:hide}} :ns? true})

(defn doc->viewer
  ([doc] (doc->viewer {} doc))
  ([{:keys [inline-results?] :or {inline-results? false}} doc]
   (let [{:keys [ns]} (meta doc)]
     (cond-> (into []
                   (mapcat (fn [{:as cell :keys [type text result ns?]}]
                             (case type
                               :markdown [(v/md text)]
                               :code (let [{:keys [code? fold? result?]} (->display cell)]
                                       (cond-> []
                                         code?
                                         (conj (cond-> (v/code text) fold? (assoc :nextjournal/viewer :code-folded)))
                                         result?
                                         (conj (cond
                                                 (v/registration? (v/value result))
                                                 (v/value result)

                                                 :else
                                                 (->result ns result (and (not inline-results?)
                                                                          (contains? result :nextjournal/blob-id))))))))))
                   doc)
       true v/notebook
       ns (assoc :scope (v/datafy-scope ns))))))

#_(meta (doc->viewer (nextjournal.clerk/eval-file "notebooks/hello.clj")))
#_(nextjournal.clerk/show! "notebooks/test.clj")
#_(nextjournal.clerk/show! "notebooks/visibility.clj")

(defonce ^{:doc "Load dynamic js from shadow or static bundle from cdn."}
  live-js?
  (when-let [prop (System/getProperty "clerk.live_js")]
    (not= "false" prop)))

(def resource->static-url
  {"/css/app.css" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VxQBDwk3cvr1bt8YVL5m6bJGrFEmzrSbCrH1roypLjJr4AbbteCKh9Y6gQVYexdY85QA2HG5nQFLWpRp69zFSPDJ9"
   "/css/viewer.css" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VvykE47cdahchdt8fxwHyYwJ7YSmEFcMSyqf4UNs61izpuF1xXpKA4HeZQctDkkU11B5iLVSBjpCQrk5f5mWXS9xv"
   "/js/viewer.js" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VwyMcfayNcoRj7RZhzLTBGyS2hAtk8Teieo3s5GyiNooj4R5JhZdgZ3GuNnSAye7STHZKzhUTfrSsVbEZx79JEFc2"})

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
window.ws_send = msg => ws.send(msg)")]]))


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
