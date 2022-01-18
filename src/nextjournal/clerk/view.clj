(ns nextjournal.clerk.view
  (:require [nextjournal.clerk.config :as config]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.hashing :as h]
            [hiccup.page :as hiccup]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as w]
            [multihash.core :as multihash]
            [multihash.digest :as digest]
            [taoensso.nippy :as nippy]))

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


(defn exceeds-bounded-count-limit? [value]
  (and (seqable? value)
       (try
         (let [limit config/*bounded-count-limit*]
           (= limit (bounded-count limit value)))
         (catch Exception _
           true))))

#_(exceeds-bounded-count-limit? (range))
#_(exceeds-bounded-count-limit? (range 10000))
#_(exceeds-bounded-count-limit? (range 1000000))
#_(exceeds-bounded-count-limit? :foo)

(defn valuehash [value]
  (-> value
      nippy/fast-freeze
      digest/sha2-512
      multihash/base58))

#_(valuehash (range 100))
#_(valuehash (zipmap (range 100) (range 100)))

(defn ->hash-str
  "Attempts to compute a hash of `value` falling back to a random string."
  [value]
  (if-let [valuehash (try
                       (when-not (exceeds-bounded-count-limit? value)
                         (valuehash value))
                       (catch Exception _))]
    valuehash
    (str (gensym))))

#_(->hash-str (range 104))
#_(->hash-str (range))

(defn selected-viewers [described-result]
  (into []
        (map (comp :render-fn :nextjournal/viewer))
        (tree-seq (comp vector? :nextjournal/value) :nextjournal/value described-result)))

(defn base64-encode-value [{:as result :nextjournal/keys [content-type]}]
  (update result :nextjournal/value (fn [data] (str "data:" content-type ";base64, "
                                                    (.encodeToString (java.util.Base64/getEncoder) data)))))

(defn ->result [ns {:nextjournal/keys [value blob-id]} lazy-load?]
  (let [described-result (v/describe value {:viewers (v/get-viewers ns (v/viewers value))})
        content-type (:nextjournal/content-type described-result)]
    (merge {:nextjournal/viewer :clerk/result
            :nextjournal/value (cond-> (try {:nextjournal/edn (->edn (cond-> described-result
                                                                       (and content-type lazy-load?)
                                                                       (assoc :nextjournal/value {:blob-id blob-id})
                                                                       (and content-type (not lazy-load?)) base64-encode-value))}
                                            (catch Throwable _e
                                              {:nextjournal/string (pr-str value)}))
                                 (-> described-result v/viewer :name)
                                 (assoc :nextjournal/viewer (select-keys (v/viewer described-result) [:name]))

                                 lazy-load?
                                 (assoc :nextjournal/fetch-opts {:blob-id blob-id}
                                        :nextjournal/hash (->hash-str [blob-id (selected-viewers described-result)])))}

           (dissoc described-result :nextjournal/value :nextjournal/viewer))))

#_(nextjournal.clerk/show! "notebooks/hello.clj")
#_(nextjournal.clerk/show! "notebooks/viewers/image.clj")

(defn ->display [{:as code-cell :keys [result ns? hidden?]}]
  (let [{:nextjournal.clerk/keys [visibility]} result
        result? (and (contains? code-cell :result)
                     (not hidden?)
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

(defn describe-inline-result [mddoc ns lazy-load?]
  (h/doc-walk :result
              (fn [node _]
                (-> node (dissoc :result) (merge (->result ns (:result node) lazy-load?))))
              mddoc))

(defn doc->viewer
  ([doc] (doc->viewer {} doc))
  ([{:keys [inline-results?] :or {inline-results? false}} doc]
   (let [{:keys [ns]} (meta doc)]
     (cond-> (into []
                   (mapcat (fn [{:as cell :keys [type text result doc]}]
                             (case type
                               :markdown [(v/md (or text (describe-inline-result doc ns (not inline-results?))))]
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

(defn include-tailwind-cdn []
  (list
   (hiccup/include-js "https://cdn.tailwindcss.com?plugins=typography")
   [:script (-> (slurp (io/resource "stylesheets/tailwind.config.js"))
                (str/replace  #"^module.exports" "tailwind.config")
                (str/replace  #"require\(.*\)" ""))]
   [:style {:type "text/tailwindcss"} (slurp (io/resource "stylesheets/viewer.css"))]))

(defn ->html [{:keys [conn-ws?] :or {conn-ws? true}} state]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (if-let [css-url (@config/!resource->url "/css/viewer.css")]
      (hiccup/include-css css-url)
      (include-tailwind-cdn))
    (hiccup/include-css "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css")
    (hiccup/include-js (@config/!resource->url "/js/viewer.js"))
    (hiccup/include-css "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css")
    (hiccup/include-css "https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;700&family=Fira+Mono:wght@400;700&family=Fira+Sans+Condensed:ital,wght@0,700;1,700&family=Fira+Sans:ital,wght@0,400;0,500;0,700;1,400;1,500;1,700&family=PT+Serif:ital,wght@0,400;0,700;1,400;1,700&display=swap")]
   [:body
    [:div#clerk]
    [:script "let viewer = nextjournal.clerk.sci_viewer
let state = " (-> state ->edn pr-str) "
viewer.set_state(viewer.read_string(state))
viewer.mount(document.getElementById('clerk'))\n"
     (when conn-ws?
       "const ws = new WebSocket(document.location.origin.replace(/^http/, 'ws') + '/_ws')
ws.onmessage = msg => viewer.set_state(viewer.read_string(msg.data))
window.ws_send = msg => ws.send(msg)")]]))

(defn ->static-app [state]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (include-tailwind-cdn)
    (hiccup/include-js (@config/!resource->url "/js/viewer.js"))
    (hiccup/include-css "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css")
    (hiccup/include-css "https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;700&family=Fira+Mono:wght@400;700&family=Fira+Sans+Condensed:ital,wght@0,700;1,700&family=Fira+Sans:ital,wght@0,400;0,500;0,700;1,400;1,500;1,700&family=PT+Serif:ital,wght@0,400;0,700;1,400;1,700&display=swap")]
   [:body
    [:div#clerk-static-app]
    [:script "let viewer = nextjournal.clerk.sci_viewer
let app = nextjournal.clerk.static_app
let opts = viewer.read_string(" (-> state ->edn pr-str) ")
app.init(opts)\n"]]))

(defn doc->html [doc error]
  (->html {} {:doc (doc->viewer {} doc) :error error}))

(defn doc->static-html [doc]
  (->html {:conn-ws? false :live-js? false} (doc->viewer {:inline-results? true} doc)))

#_(let [out "test.html"]
    (spit out (doc->static-html (nextjournal.clerk/eval-file "notebooks/pagination.clj")))
    (clojure.java.browse/browse-url out))
