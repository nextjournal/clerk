(ns nextjournal.clerk.view
  (:require [nextjournal.clerk.config :as config]
            [nextjournal.clerk.viewer :as v]
            [hiccup.page :as hiccup]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn doc->viewer
  ([doc] (doc->viewer {} doc))
  ([opts {:as doc :keys [ns file]}]
   (binding [*ns* ns]
     (-> (merge doc opts) v/notebook v/present))))


#_(doc->viewer (nextjournal.clerk/eval-file "notebooks/hello.clj"))
#_(nextjournal.clerk/show! "notebooks/test.clj")
#_(nextjournal.clerk/show! "notebooks/visibility.clj")

(defn include-viewer-css []
  (if-let [css-url (@config/!resource->url "/css/viewer.css")]
    (hiccup/include-css css-url)
    (list (hiccup/include-js "https://cdn.tailwindcss.com?plugins=typography")
          [:script (-> (slurp (io/resource "stylesheets/tailwind.config.js"))
                       (str/replace  #"^module.exports" "tailwind.config")
                       (str/replace  #"require\(.*\)" ""))]
          [:style {:type "text/tailwindcss"} (slurp (io/resource "stylesheets/viewer.css"))])))

(defn include-css+js []
  (list
   (include-viewer-css)
   [:script {:type "module" :src (@config/!resource->url "/js/viewer.js")}]
   (hiccup/include-css "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css")
   (hiccup/include-css "https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;700&family=Fira+Mono:wght@400;700&family=Fira+Sans+Condensed:ital,wght@0,700;1,700&family=Fira+Sans:ital,wght@0,400;0,500;0,700;1,400;1,500;1,700&family=PT+Serif:ital,wght@0,400;0,700;1,400;1,700&display=swap")))

(defn ->html [{:keys [conn-ws?] :or {conn-ws? true}} state]
  (hiccup/html5
   {:class "overflow-hidden min-h-screen"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (include-css+js)]
   [:body.dark:bg-gray-900
    [:div#clerk]
    [:script {:type "module"} "let viewer = nextjournal.clerk.sci_env
let state = " (-> state v/->edn pr-str) "
viewer.set_state(viewer.read_string(state))
viewer.mount(document.getElementById('clerk'))\n"
     (when conn-ws?
       "const ws = new WebSocket(document.location.origin.replace(/^http/, 'ws') + '/_ws')
ws.onmessage = msg => viewer.set_state(viewer.read_string(msg.data))
window.ws_send = msg => ws.send(msg)")]]))

(defn ->static-app [{:as state :keys [current-path]}]
  (hiccup/html5
   {:class "overflow-hidden min-h-screen"}
   [:head
    [:title (or (and current-path (-> state :path->doc (get current-path) v/->value :title)) "Clerk")]
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (when current-path (v/open-graph-metas (-> state :path->doc (get current-path) v/->value :open-graph)))
    (include-css+js)]
   [:body
    [:div#clerk-static-app]
    [:script {:type "module"} "let viewer = nextjournal.clerk.sci_env
let app = nextjournal.clerk.static_app
let opts = viewer.read_string(" (-> state v/->edn pr-str) ")
app.init(opts)\n"]]))

(defn doc->html [doc error]
  (->html {} {:doc (doc->viewer {} doc) :error error}))

(defn doc->static-html [doc]
  (->html {:conn-ws? false} {:doc (doc->viewer {:inline-results? true} doc)}))

#_(let [out "test.html"]
    (spit out (doc->static-html (nextjournal.clerk.eval/eval-file "notebooks/pagination.clj")))
    (clojure.java.browse/browse-url out))
