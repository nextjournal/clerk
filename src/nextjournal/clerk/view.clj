(ns nextjournal.clerk.view
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.walk :as w]
   [hiccup.page :as hiccup]
   [multihash.core :as multihash]
   [multihash.digest :as digest]
   [nextjournal.clerk.config :as config]
   [nextjournal.clerk.viewer :as v]
   [taoensso.nippy :as nippy])
  (:import (java.util Base64)))

(set! *warn-on-reflection* true)

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
                                                    (.encodeToString (Base64/getEncoder) data)))))

(defn apply-viewer-unwrapping-var-from-def [{:as result :nextjournal/keys [value viewer]}]
  (if viewer
    (let [{:keys [transform-fn]} (and (map? viewer) viewer)
          value (if (and (not transform-fn) (get value :nextjournal.clerk/var-from-def))
                  (-> value :nextjournal.clerk/var-from-def deref)
                  value)]
      (assoc result :nextjournal/value (if (or (var? viewer) (fn? viewer))
                                         (viewer value)
                                         {:nextjournal/value value
                                          :nextjournal/viewer (v/normalize-viewer viewer)})))
    result))

#_(apply-viewer-unwrapping-var-from-def {:nextjournal/value [:h1 "hi"] :nextjournal/viewer :html})
#_(apply-viewer-unwrapping-var-from-def {:nextjournal/value [:h1 "hi"] :nextjournal/viewer (resolve 'nextjournal.clerk/html)})

(defn extract-blobs [lazy-load? blob-id described-result]
  (w/postwalk #(cond-> %
                 (and (get % :nextjournal/content-type) lazy-load?)
                 (assoc :nextjournal/value {:blob-id blob-id :path (:path %)})
                 (and (get % :nextjournal/content-type) (not lazy-load?))
                 base64-encode-value)
              described-result))

(defn ->result [ns {:as result :nextjournal/keys [value viewers blob-id]} lazy-load?]
  (let [described-result (extract-blobs lazy-load? blob-id (v/describe value {:viewers (concat viewers (v/get-viewers ns (v/viewers value)))}))
        opts-from-form-meta (select-keys result [:nextjournal/width])]
    (merge {:nextjournal/viewer :clerk/result
            :nextjournal/value (cond-> (try {:nextjournal/edn (->edn described-result)}
                                            (catch Throwable _e
                                              {:nextjournal/string (pr-str value)}))
                                 (-> described-result v/viewer :name)
                                 (assoc :nextjournal/viewer (select-keys (v/viewer described-result) [:name]))

                                 lazy-load?
                                 (assoc :nextjournal/fetch-opts {:blob-id blob-id}
                                        :nextjournal/hash (->hash-str [blob-id described-result])))}
           (dissoc described-result :nextjournal/value :nextjournal/viewer)
           opts-from-form-meta)))

#_(nextjournal.clerk/show! "notebooks/hello.clj")
#_(nextjournal.clerk/show! "notebooks/viewers/image.clj")

(defn result-hidden? [result]
  (= :hide-result (-> result v/value v/viewer)))

(defn ->display [{:as code-cell :keys [result ns?]}]
  (let [{:nextjournal.clerk/keys [visibility]} result
        result? (and (contains? code-cell :result)
                     (not (result-hidden? result))
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

(defn describe-block [{:keys [inline-results?] :or {inline-results? false}} {:keys [ns]} {:as cell :keys [type text doc]}]
  (case type
    :markdown [(v/md (or doc text))]
    :code (let [{:as cell :keys [result]} (update cell :result apply-viewer-unwrapping-var-from-def)
                {:keys [code? fold? result?]} (->display cell)]
            (cond-> []
              code?
              (conj (cond-> (v/code text) fold? (assoc :nextjournal/viewer :code-folded)))
              result?
              (conj (cond
                      (v/registration? (v/value result))
                      (v/value result)
                      :else
                      (->result ns result (and (not inline-results?)
                                               (contains? result :nextjournal/blob-id)))))))))

(defn doc->viewer
  ([doc] (doc->viewer {} doc))
  ([{:as opts :keys [toc?] :or {toc? false}} {:as doc :keys [ns]}]
   (-> doc
       (update :blocks #(into [] (mapcat (partial describe-block opts doc)) %))
       (select-keys [:blocks :toc :title])
       (cond-> (not toc?) (dissoc :toc))
       v/notebook
       (cond-> ns (assoc :scope (v/datafy-scope ns))))))

#_(doc->viewer (nextjournal.clerk/eval-file "notebooks/hello.clj"))
#_(nextjournal.clerk/show! "notebooks/test.clj")
#_(nextjournal.clerk/show! "notebooks/visibility.clj")

(def asset-map (:asset-map
                (edn/read-string (slurp (io/resource "asset_manifest.edn")))))

(defn cache-url!
  "Reads url from asset_manifest.edn and caches it locally. Returns file-name of cached file."
  [url]
  (if-let [gurl (get asset-map url)]
    (let [fname (last (str/split gurl #"/"))
          local-file (fs/file (str ".clerk/.cache/assets/" fname))]
      (if (fs/exists? local-file)
        fname
        (do (fs/create-dirs (fs/parent local-file))
            (spit local-file (slurp gurl))
            fname)))
    (do (binding [*out* *err*]
          (println "[clerk] WARNING - url does not exist in manifest: " url))
        nil)))

(defn cached-url [url]
  (or (some->>
       (cache-url! url)
       (str "/cached/"))
      (binding [*out* *err*]
        (println "[clerk] WARNING - uncached url:" url)
        url)))

(defn include-viewer-css [cached?]
  (if-let [css-url (config/resource->url "/css/viewer.css")]
    (hiccup/include-css css-url)
    (list (hiccup/include-js
           ((if cached?
              cached-url
              identity) "https://cdn.tailwindcss.com/3.0.23?plugins=typography@0.5.2"))
          [:script (-> (slurp (io/resource "stylesheets/tailwind.config.js"))
                       (str/replace  #"^module.exports" "tailwind.config")
                       (str/replace  #"require\(.*\)" ""))]
          [:style {:type "text/tailwindcss"} (slurp (io/resource "stylesheets/viewer.css"))])))

(defn ->html [{:keys [conn-ws?] :or {conn-ws? true}} state]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (include-viewer-css true)
    (hiccup/include-css (cached-url "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css"))
    (hiccup/include-js (config/resource->url "/js/viewer.js"))
    (hiccup/include-css (cached-url "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css"))
    (hiccup/include-css (cached-url "https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;700&family=Fira+Mono:wght@400;700&family=Fira+Sans+Condensed:ital,wght@0,700;1,700&family=Fira+Sans:ital,wght@0,400;0,500;0,700;1,400;1,500;1,700&family=PT+Serif:ital,wght@0,400;0,700;1,400;1,700&display=swap"))]
   [:body.dark:bg-slate-900
    [:div#clerk]
    [:script "let viewer = nextjournal.clerk.sci_viewer
let state = " (-> state ->edn pr-str) "
viewer.set_state(viewer.read_string(state))
viewer.mount(document.getElementById('clerk'))\n"
     (when conn-ws?
       "const ws = new WebSocket(document.location.origin.replace(/^http/, 'ws') + '/_ws')
ws.onmessage = msg => viewer.set_state(viewer.read_string(msg.data))
window.ws_send = msg => ws.send(msg)")]]))

(defn ->static-app [{:as state :keys [current-path]}]
  (hiccup/html5
   [:head
    [:title (or (and current-path (-> state :path->doc (get current-path) v/value :title)) "Clerk")]
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (include-viewer-css false)
    (hiccup/include-js (config/resource->url "/js/viewer.js"))
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
