(ns nextjournal.clerk.view
  (:require [nextjournal.clerk.config :as config]
            [nextjournal.clerk.viewer :as v]
            [hiccup.page :as hiccup]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.net URI)))

(defn doc->viewer
  ([doc] (doc->viewer {} doc))
  ([opts {:as doc :keys [ns file]}]
   (binding [*ns* ns]
     (-> (merge doc opts) v/notebook v/present))))

#_(doc->viewer (nextjournal.clerk/eval-file "notebooks/hello.clj"))
#_(nextjournal.clerk/show! "notebooks/test.clj")
#_(nextjournal.clerk/show! "notebooks/visibility.clj")

(defn relative? [url]
  (and (not (.isAbsolute (URI. url)))
       (not (str/starts-with? url "/"))))

#_(relative? "/hello/css")
#_(relative? "hello/css")
#_(relative? "https://cdn.stylesheet.css")

(defn adjust-relative-path [{:as state :keys [current-path]} url]
  (cond->> url
    (and current-path (relative? url))
    (str (v/relative-root-prefix-from (v/map-index state current-path)))))

(defn include-viewer-css [state]
  (if-let [css-url (get-in state [:resource->url "/css/viewer.css"])]
    (hiccup/include-css (adjust-relative-path state css-url))
    (list (hiccup/include-js "https://cdn.tailwindcss.com?plugins=typography")
          [:script (-> (slurp (io/resource "stylesheets/tailwind.config.js"))
                       (str/replace #"^module.exports" "tailwind.config")
                       (str/replace #"require\(.*\)" ""))]
          [:style {:type "text/tailwindcss"} (slurp (io/resource "stylesheets/viewer.css"))])))

(defn include-css+js [state]
  (list
   (include-viewer-css state)
   [:script {:type "module" :src (adjust-relative-path state (get-in state [:resource->url "/js/viewer.js"]))}]
   (hiccup/include-css "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css")
   [:link {:rel "preconnect" :href "https://fonts.bunny.net"}]
   (hiccup/include-css "https://fonts.bunny.net/css?family=fira-mono:400,700%7Cfira-sans:400,400i,500,500i,700,700i%7Cfira-sans-condensed:700,700i%7Cpt-serif:400,400i,700,700i")))

(defn ->html [{:as state :keys [conn-ws?] :or {conn-ws? true}}]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (include-css+js state)]
   [:body.dark:bg-gray-900
    [:div#clerk]
    [:script {:type "module"} "let viewer = nextjournal.clerk.sci_env
let state = " (-> state v/->edn pr-str) "
viewer.set_state(viewer.read_string(state))
viewer.mount(document.getElementById('clerk'))\n"
     (when conn-ws?
       "const ws = new WebSocket(document.location.origin.replace(/^http/, 'ws') + '/_ws')
ws.onmessage = viewer.onmessage;
window.ws_send = msg => ws.send(msg)")]]))

(defn ->static-app [{:as state :keys [current-path html]}]
  (hiccup/html5
   [:head
    [:title (or (and current-path (-> state :path->doc (get current-path) v/->value :title)) "Clerk")]
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (when current-path (v/open-graph-metas (-> state :path->doc (get current-path) v/->value :open-graph)))
    (include-css+js state)]
   [:body
    [:div#clerk-static-app html]
    [:script {:type "module"} "let viewer = nextjournal.clerk.sci_env
let app = nextjournal.clerk.static_app
let opts = viewer.read_string(" (-> state v/->edn pr-str) ")
app.init(opts)\n"]]))

(defn doc->html [opts]
  (->html (-> opts
              (update :doc doc->viewer)
              (assoc :resource->url @config/!resource->url))))
