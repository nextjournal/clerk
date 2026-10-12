(ns nextjournal.clerk.datastar
  "Datastar frontends, `:frontend :datastar+sci-react-island` (default) and `:frontend :datastar`.

  The page is a pure function of `webserver/!state` and a tab id. On every
  state change a render loop re-renders `<main id=\"clerk\">` for each
  connected tab and pushes it over a compressed SSE stream, Datastar then
  morphs it into the DOM. Actions only update state and return 204.

  Per tab ui state lives in `(:tabs @webserver/!state)`, keyed by a tab id
  that's kept in the `tab` query param so it survives reloads."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [editscript.core :as editscript]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup.core :as hiccup1]
            [hiccup.page]
            [hiccup2.core :as h]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.html :as html]
            [nextjournal.clerk.utils :as u]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver]
            [org.httpkit.server :as httpkit])
  (:import (java.io ByteArrayOutputStream)
           (java.nio.charset StandardCharsets)
           (java.util.concurrent ExecutorService LinkedBlockingQueue TimeUnit)
           (java.util.zip GZIPOutputStream)))

(def datastar-url "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.4/bundles/datastar.js")

(def katex-css-url "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css")

;; ## Tab state

(defn new-tab-id []
  (subs (str (java.util.UUID/randomUUID)) 0 8))

(defn valid-tab-id? [s]
  (boolean (and (string? s) (re-matches #"[A-Za-z0-9-]{1,36}" s))))

(defn result-index
  "Returns a map of result id to `{:hash :blob-id :presented}` for all results of the `presented` doc."
  [presented]
  (into {}
        (keep (fn [x]
                (when (and (map? x) (v/get-safe x :nextjournal/presented) (v/get-safe x :nextjournal/blob-id))
                  (let [presented (:nextjournal/presented x)]
                    (when-let [id (get-in presented [:nextjournal/render-opts :id])]
                      [id {:hash (:nextjournal/hash x)
                           :blob-id (:nextjournal/blob-id x)
                           :presented presented}])))))
        (tree-seq coll? seq (:nextjournal/value presented))))

(defn presented-paths [presented]
  (into #{}
        (keep #(when (map? %) (:path %)))
        (tree-seq (some-fn map? vector?) #(if (map? %) [(:nextjournal/value %)] %) presented)))

(defn prune-tab
  "Drops ui state of results that are gone. When a result changed, drops
  its fetched elisions and flipped paths that no longer exist."
  [tab index]
  (update tab :results
          (fn [results]
            (into {}
                  (keep (fn [[id {:as result-state :keys [hash flipped]}]]
                          (when-let [{new-hash :hash :keys [presented]} (index id)]
                            (if (= hash new-hash)
                              [id result-state]
                              [id {:hash new-hash :flipped (into #{} (filter (presented-paths presented)) flipped)}]))))
                  results))))

(defn prune-tabs [tabs presented]
  (let [index (result-index presented)]
    (into {} (map (fn [[tab-id tab]] [tab-id (prune-tab tab index)])) tabs)))

(defn update-result-state
  "Updates the tab's state of result `id` with `f`, resetting it when the result's hash changed."
  [state tab-id id f]
  (if-let [{:keys [hash]} (get (result-index (:presented state)) id)]
    (update-in state [:tabs tab-id :results id]
               (fn [result-state]
                 (f (if (= hash (:hash result-state)) result-state {:hash hash}))))
    state))

(defn toggle [s x]
  ((if (contains? s x) disj conj) (or s #{}) x))

;; ## Rendering

(defn query-string [params]
  (str/join "&" (map (fn [[k v]] (str (name k) "=" (html/url-encode (if (string? v) v (pr-str v))))) params)))

(defn action-url [tab-id action params]
  (str "/_clerk/action/" (name action) "?" (query-string (cond-> params tab-id (assoc :tab tab-id)))))

(defn action-attrs [tab-id action params]
  {:data-on:click (str "@post('" (action-url tab-id action params) "')")})

(defn eval-attrs
  "Returns attributes for an element in `clerk/html` that evaluate `form`
  in the notebook's namespace on the server when `:event` (default
  `:click`) fires. Pass `:recompute? true` to recompute the notebook
  afterwards. Only supported with the Datastar frontends."
  ([form] (eval-attrs {} form))
  ([{:keys [event recompute?] :or {event :click}} form]
   {(keyword (str "data-on:" (name event)))
    (str "@post('" (action-url nil :eval (cond-> {:form form} recompute? (assoc :recompute true))) "')")}))

(declare js-str)

(defn sync-attrs
  "Attrs for an input bound to `sync-var`. The server's `value` is applied
  via `data-init`, which Datastar re-runs when it changes, but only while
  the input isn't focused. So the client owns the value while the user is
  typing or dragging and stale renders can't clobber it."
  [tab-id sync-var coerce value]
  {:data-init (str "if (document.activeElement !== el) el.value = " (js-str value))
   (keyword (if (= :string coerce) "data-on:input__debounce.150ms" "data-on:input__throttle.50ms.trailing"))
   (str "@post('" (action-url tab-id :sync {:var (symbol sync-var) :coerce coerce}) "&seq=' + Math.round(performance.timeOrigin + performance.now()) + '&value=' + encodeURIComponent(el.value))")})

(defn result-state-fn [tab]
  (fn [id presented auto-expand?]
    (let [{:keys [flipped fetched]} (get-in tab [:results id])
          presented' (reduce (fn [p [elision more]] (v/merge-presentations p more elision)) presented fetched)]
      {:presented presented'
       :expanded-at (reduce #(update %1 %2 not) (html/->expanded-at auto-expand? presented') flipped)})))

(defn live-islands?
  "Does the running server use SCI + React islands (`:frontend :datastar+sci-react-island`)?"
  []
  (webserver/islands? @webserver/!server))

(defn render-ctx [tab-id tab]
  {:islands? (live-islands?)
   :result-state (result-state-fn tab)
   :on-toggle (fn [{:keys [result-id]} path]
                (when result-id
                  (action-attrs tab-id :toggle-expand {:id result-id :path path})))
   :on-fetch (fn [{:keys [result-id]} elision]
               (when result-id
                 (action-attrs tab-id :fetch-more {:id result-id :elision elision})))
   :on-sync (fn [_ctx sync-var coerce value]
              (sync-attrs tab-id sync-var coerce value))})

(defn island-env
  "Element carrying what islands need: the notebook's synced atoms and required cljs."
  [presented]
  (let [{:keys [atom-var-name->state] ns-eval :ns} (:nextjournal/value presented)
        {:keys [cljs-libs]} presented]
    [:clerk-island-env (cond-> {:libs (v/->edn (into (vec cljs-libs) (some-> ns-eval vector)))}
                         (seq atom-var-name->state) (assoc :state (v/->edn atom-var-name->state)))]))

(defn render-error-page
  "Renders a full page error for an exception thrown while rendering a notebook."
  [e]
  [:div.flex.justify-center.min-h-screen.px-8.py-16.font-sans
   [:div.w-full.max-w-prose
    [:h1.text-xl.font-bold.text-red-600.dark:text-red-300.mb-4 "Clerk couldn't render this notebook"]
    [:div.bg-red-100.dark:bg-gray-800.px-6.py-4.rounded-md.text-sm.dark:border-2.dark:border-red-300
     [:p.font-mono.text-red-600.dark:text-red-300 (ex-message e)]
     (when-let [data (not-empty (ex-data e))]
       [:pre.mt-4.text-xs.text-red-600.dark:text-red-300.whitespace-pre-wrap (pr-str data)])]]])

(defn render-main [{:keys [presented status tabs]} tab-id]
  ;; not `#clerk` which the cljs viewer bundle would mount itself onto
  [:main#clerk-datastar
   (when (and presented (live-islands?)) (island-env presented))
   (when status (html/render-status status))
   (when presented
     ;; rendered to a string here so errors from lazily rendered children are caught too
     (try (h/raw (html/->html-str (html/inspect (assoc (render-ctx tab-id (get tabs tab-id)) :ns (html/doc-ns presented)) presented)))
          (catch Exception e
            (render-error-page e))))])

(defn throw-if-not-renderable!
  "Renders `presented` once to throw when it can't be rendered with the current frontend,
  e.g. a value whose viewer needs the browser with `:frontend :datastar`. Called when
  a doc is presented so the error is thrown at the REPL."
  [presented]
  (html/->html-str (html/inspect {:islands? (live-islands?) :ns (html/doc-ns presented)} presented)))

(defn replace-url-script [tab-id]
  (str "{const u = new URL(location); u.searchParams.set('tab', '" tab-id "'); history.replaceState(history.state, '', u)}"))

(defn js-str
  "Returns `s` as a JavaScript string literal that's safe to put into a script tag."
  [s]
  (str/replace (pr-str (str s)) "</" "<\\/"))

(defn push-url-script
  "Pushes a history entry for `nav-path` unless the browser is already there, keeping the query string (tab id)."
  [nav-path title]
  (str "{const p = '/' + " (js-str nav-path) "; "
       "if (decodeURIComponent(location.pathname) !== p) {const u = new URL(location); u.pathname = p; u.hash = ''; history.pushState(null, '', u)} "
       "document.documentElement.dataset.clerkPath = p; "
       "document.title = " (js-str (or title "Clerk")) "}"))

(defn navigate-expr
  "Datastar expression navigating to the path given by the JavaScript expression `path-expr`."
  [path-expr]
  (str "@post('/_clerk/action/navigate?tab=' + new URL(location).searchParams.get('tab') + '&path=' + encodeURIComponent(" path-expr "))"))

(def link-click-expr
  ;; intercept same-origin links to notebooks, let the browser handle everything else
  (str "const a = evt.target.closest('a'); "
       "if (a && a.origin === location.origin && !a.target && !a.hasAttribute('download') "
       "&& !evt.metaKey && !evt.ctrlKey && !evt.shiftKey && !evt.altKey && !a.pathname.startsWith('/_') "
       "&& !(a.hash && a.pathname === location.pathname)) "
       "{ evt.preventDefault(); " (navigate-expr "decodeURIComponent(a.pathname).slice(1)") " }"))

(def page-css
  ".cm-line { padding: 0; line-height: 1.6; font-size: 15px; font-family: \"Fira Mono\", monospace; }
#clerk-toc-panel { display: none; position: fixed; top: 0; left: 0; z-index: 10; width: 220px; height: 100vh; }
html[data-clerk-toc=open] #clerk-toc-panel { display: block; }
html[data-clerk-toc=open] #clerk-toc-open-button { display: none; }
@media (min-width: 768px) { html[data-clerk-toc=open] .notebook-viewer { margin-left: 220px; } }
@media (max-width: 767px) { #clerk-toc-panel { box-shadow: 0 20px 25px -5px rgb(0 0 0 / 0.1); } }")

(defn early-js
  "Applies dark mode and the toc state before the first paint to avoid flashes."
  [toc-default]
  (str "if (localStorage.getItem('clerk-darkmode') === 'true') document.documentElement.classList.add('dark');"
       (when toc-default
         (str "{const t = localStorage.getItem('clerk-navbar');"
              " document.documentElement.dataset.clerkToc = t === null ? " (js-str toc-default) " : t === 'true' ? 'open' : 'closed'}"))))

(defn toc-default
  "The initial toc state of a presented doc, nil when it has no toc."
  [presented]
  (let [{:keys [toc-visibility toc]} (:nextjournal/value presented)]
    (when (and toc-visibility (seq (:nextjournal/value toc)))
      (if (= :collapsed toc-visibility) "closed" "open"))))

(def fonts-css-url
  "https://fonts.bunny.net/css?family=fira-mono:400,700%7Cfira-sans:400,400i,500,500i,700,700i%7Cfira-sans-condensed:700,700i%7Cpt-serif:400,400i,700,700i")

(defn components-js []
  (slurp (io/resource "nextjournal/clerk/datastar/components.js")))

(defn page-html*
  "Page shell shared by the live and static pages. `opts` are like `view/->html`
  state (`:resource->url`, `:current-path` for relative urls of directory builds)."
  [{:as opts :keys [resource->url islands?]} {:as page :keys [title open-graph toc-default head body-attrs body]}]
  (str "<!DOCTYPE html>\n"
       (h/html {:mode :html}
               [:html
                [:head
                 [:meta {:charset "UTF-8"}]
                 [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                 [:title (or title "Clerk")]
                 (when open-graph (v/open-graph-metas open-graph))
                 ;; `view` returns hiccup for `hiccup.core`, which doesn't escape script contents
                 (h/raw (hiccup1/html {:mode :html} (view/include-viewer-css opts)))
                 (h/raw (hiccup1/html {:mode :html} (hiccup.page/include-css fonts-css-url katex-css-url)))
                 [:style (h/raw page-css)]
                 [:script (h/raw (str (early-js toc-default) (some->> (:early-js page) (str ";"))))]
                 (when islands?
                   [:meta {:name "clerk-viewer-js" :content (view/adjust-relative-path opts (get resource->url "/js/viewer.js"))}])
                 head]
                (into [:body.dark:bg-gray-900 body-attrs] body)])))

(defn page-html [{:as state :keys [presented]} tab-id {:keys [replace-url?]}]
  (page-html* {:resource->url @config/!resource->url :islands? (live-islands?)}
              {:title (-> presented :nextjournal/value :title)
               :toc-default (toc-default presented)
               :head (list [:script {:type "module" :src datastar-url}]
                           [:script {:type "module" :src "/_clerk/components.js"}]
                           ;; where islands send their messages, absent in static builds
                           (when (live-islands?)
                             [:meta {:name "clerk-island-endpoint" :content "/_clerk/island"}]))
               :body-attrs {:data-init (str "@get('/_clerk/sse?tab=" tab-id "', {openWhenHidden: true, retryMaxCount: Infinity})")
                            :data-on:click link-click-expr
                            ;; `popstate` also fires for in-page anchors (e.g. toc links), only navigate when the path changed
                            :data-on:popstate__window (str "if (decodeURIComponent(location.pathname) !== document.documentElement.dataset.clerkPath) "
                                                           (navigate-expr "decodeURIComponent(location.pathname).slice(1)"))}
               :early-js "document.documentElement.dataset.clerkPath = decodeURIComponent(location.pathname)"
               :body [(when replace-url?
                        [:script (h/raw (replace-url-script tab-id))])
                      (render-main state tab-id)]}))

;; ## Static builds
;; Pages rendered once without a server: no Datastar, expansion is fixed at
;; its initial state, client components and islands (with
;; `:frontend :datastar+sci-react-island`) still work.

(defn render-static-doc [{:keys [islands?]} presented]
  (html/inspect {:islands? islands? :ns (html/doc-ns presented)} presented))

(defn static-doc-html
  "The html of a presented doc in a static build with build `opts`, also used to compile its css."
  [opts presented]
  (html/->html-str (render-static-doc {:islands? (webserver/islands? opts)} presented)))

(def single-file-router-js
  ;; Shows the notebook for the `#/path` written by `doc-url` in single file builds. Each
  ;; notebook is kept in a <template> so only the shown one is in the DOM (and its
  ;; components and islands only start when it's shown).
  "const page = document.getElementById('clerk-page')
const show = () => {
  // leave in-page anchors like toc links to the browser
  if (location.hash && !location.hash.startsWith('#/')) return
  const path = decodeURIComponent(location.hash.replace(/^#\\/?/, '').split(':')[0])
  const templates = [...document.querySelectorAll('template[data-clerk-path]')]
  const template = templates.find(t => t.dataset.clerkPath === path) || templates.find(t => t.dataset.clerkPath === '')
  if (template && page.dataset.clerkPath !== template.dataset.clerkPath) {
    page.replaceChildren(template.content.cloneNode(true))
    page.dataset.clerkPath = template.dataset.clerkPath
    document.title = template.dataset.clerkTitle
    scrollTo(0, 0)
  }
}
addEventListener('hashchange', show)
show()")

(defn merged-island-env
  "One island env for several presented docs (single file builds)."
  [presented-docs]
  (island-env {:cljs-libs (into [] (mapcat :cljs-libs) presented-docs)
               :nextjournal/value {:atom-var-name->state (into {} (map (comp :atom-var-name->state :nextjournal/value)) presented-docs)}}))

(defn static-page-html
  "Renders the static html for a single `:presented` doc (directory builds)
  or all docs in `:path->doc` (single file builds)."
  [opts {:keys [presented path->doc]}]
  (let [opts (assoc opts :islands? (webserver/islands? opts))]
    (if path->doc
      (page-html* opts
                  {:title (-> (get path->doc "") :nextjournal/value :title)
                   :head [:script {:type "module"} (h/raw (components-js))]
                   :body [[:main#clerk-datastar
                           (when (:islands? opts) (merged-island-env (vals path->doc)))
                           [:div#clerk-page]
                           (for [[path presented] (sort-by key path->doc)]
                             [:template {:data-clerk-path path
                                         :data-clerk-title (or (-> presented :nextjournal/value :title) "Clerk")}
                              (render-static-doc opts presented)])]
                          [:script {:type "module"} (h/raw single-file-router-js)]]})
      (page-html* opts
                  {:title (-> presented :nextjournal/value :title)
                   :open-graph (-> presented :nextjournal/value :open-graph)
                   :toc-default (toc-default presented)
                   :head [:script {:type "module"} (h/raw (components-js))]
                   :body [[:main#clerk-datastar
                           (when (:islands? opts) (island-env presented))
                           (render-static-doc opts presented)]]}))))

(defn write-static-app!
  "Writes the static html for a build, called by `builder/write-static-app!` when building with a Datastar frontend."
  [{:as static-app-opts :keys [package out-path path->doc]}]
  (if (= :single-file package)
    (spit (str (fs/file out-path "index.html")) (static-page-html static-app-opts {:path->doc path->doc}))
    (doseq [[path presented] path->doc]
      (let [out-html (fs/file out-path path "index.html")]
        (fs/create-dirs (fs/parent out-html))
        (spit out-html (static-page-html (assoc static-app-opts :current-path path) {:presented presented}))))))

(defn serve-page
  "Responds with the full page for the current notebook, called after `show!` in `webserver/serve-notebook`."
  [{:keys [query-string]}]
  (let [tab-id (get (webserver/query-string->map query-string) :tab)
        valid? (valid-tab-id? tab-id)
        tab-id (if valid? tab-id (new-tab-id))]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8" "Cache-Control" "no-store"}
     :body (page-html @webserver/!state tab-id {:replace-url? (not valid?)})}))

;; ## SSE

(defn sse-event
  "Formats a `datastar-patch-elements` event. `opts` are datastar data lines like `{:selector \"body\" :mode \"append\"}`."
  ([html-str] (sse-event {} html-str))
  ([opts html-str]
   (str "event: datastar-patch-elements\n"
        (apply str (map (fn [[k v]] (str "data: " (name k) " " v "\n")) opts))
        (apply str (map #(str "data: elements " % "\n") (str/split html-str #"\r\n|\r|\n" -1)))
        "\n")))

(defn script-event [js]
  (sse-event {:selector "body" :mode "append"}
             (html/->html-str [:script {:data-effect "el.remove()"} (h/raw js)])))

;; ### Compression
;; One compression context per connection, flushed after every event so
;; repeated full renders compress extremely well. gzip ships with the JDK,
;; other encodings can be added via `register-encoder!`.

(defn gzip-encoder []
  (let [out (ByteArrayOutputStream.)
        gz (GZIPOutputStream. out 8192 true)]
    {:content-encoding "gzip"
     :encode (fn [^bytes bs]
               (locking out
                 (.write gz bs)
                 (.flush gz)
                 (let [compressed (.toByteArray out)]
                   (.reset out)
                   compressed)))
     :close (fn [] (try (.close gz) (catch Exception _)))}))

(def identity-encoder
  {:encode identity :close (fn [])})

(defonce ^{:doc "Encoders by `Content-Encoding` in order of preference, each a 0-arity fn returning `{:content-encoding :encode :close}`."}
  !encoders
  (atom [["gzip" gzip-encoder]]))

(defn register-encoder!
  "Registers a stream encoder ctor for `content-encoding`, preferring it over the existing ones."
  [content-encoding ctor]
  (swap! !encoders #(into [[content-encoding ctor]] (remove (comp #{content-encoding} first)) %)))

(defn negotiate-encoder [accept-encoding]
  (let [accepted (into #{} (map #(str/trim (first (str/split % #";")))) (str/split (or accept-encoding "") #","))]
    (or (some (fn [[content-encoding ctor]] (when (accepted content-encoding) (ctor))) @!encoders)
        identity-encoder)))

;; ### Connections & render loop

(defonce ^{:doc "Connected SSE channels to `{:tab-id :encoder :last-inputs}`."}
  !conns
  (atom {}))

(defonce ^{:doc "Tab id to the time it was last connected, for pruning ui state of closed tabs."}
  !tab-last-seen
  (atom {}))

(def tab-grace-ms (* 30 60 1000))

(defonce ^LinkedBlockingQueue render-queue (LinkedBlockingQueue.))

(def render-tick-ms 16)

(defn request-render! []
  (.offer render-queue true))

(defn send-event! [ch event-str]
  (when-let [{:keys [encoder]} (get @!conns ch)]
    (httpkit/send! ch ((:encode encoder) (.getBytes ^String event-str StandardCharsets/UTF_8)) false)))

(defn render-inputs [state tab-id]
  [(:presented state) (:status state) (get-in state [:tabs tab-id])])

(defn render-conn! [state ch]
  (when-let [{:keys [tab-id last-inputs nav-path]} (get @!conns ch)]
    (let [inputs (render-inputs state tab-id)
          nav-path' (get-in state [:doc :nav-path])]
      (when (and nav-path' (not= nav-path nav-path'))
        (send-event! ch (script-event (push-url-script nav-path' (-> state :presented :nextjournal/value :title))))
        (swap! !conns (fn [conns] (cond-> conns (contains? conns ch) (assoc-in [ch :nav-path] nav-path')))))
      (when-not (and last-inputs (every? true? (map identical? inputs last-inputs)))
        (send-event! ch (sse-event (html/->html-str (render-main state tab-id))))
        (swap! !conns (fn [conns] (cond-> conns (contains? conns ch) (assoc-in [ch :last-inputs] inputs))))))))

(defn virtual-thread-executor
  "Returns a virtual thread per task executor when running on JDK 21+, nil otherwise (and in babashka)."
  ^ExecutorService []
  (u/if-bb
   nil
   (try (clojure.lang.Reflector/invokeStaticMethod java.util.concurrent.Executors "newVirtualThreadPerTaskExecutor" (object-array 0))
        (catch Throwable _ nil))))

(defonce render-executor (delay (virtual-thread-executor)))

(defn render-all! []
  (let [state @webserver/!state
        render-fns (map (fn [ch] #(try (render-conn! state ch)
                                       (catch Throwable t
                                         (binding [*out* *err*] (println "Clerk failed to render tab" (ex-message t))))))
                        (keys @!conns))]
    (if-let [^ExecutorService executor @render-executor]
      (run! deref (.invokeAll executor ^java.util.Collection (vec render-fns)))
      (run! #(%) render-fns))))

(declare !sync-seqs)

(defn prune-closed-tabs! []
  (let [live (into #{} (map :tab-id) (vals @!conns))
        now (System/currentTimeMillis)
        stale? (fn [tab-id] (and (not (live tab-id))
                                 (< tab-grace-ms (- now (get @!tab-last-seen tab-id 0)))))]
    (when (some stale? (keys (:tabs @webserver/!state)))
      (swap! webserver/!state update :tabs #(into {} (remove (comp stale? key)) %))
      (swap! !tab-last-seen #(into {} (remove (comp stale? key)) %))
      (swap! !sync-seqs #(into {} (remove (comp stale? first key)) %)))))

(defn remove-conn! [ch]
  (when-let [{:keys [tab-id encoder]} (get @!conns ch)]
    (swap! !conns dissoc ch)
    (swap! !tab-last-seen assoc tab-id (System/currentTimeMillis))
    ((:close encoder))))

(def ^:private async-channel-key-field
  (u/if-bb
   nil
   (try (doto (.getDeclaredField org.httpkit.server.AsyncChannel "key") (.setAccessible true))
        (catch Exception _ nil))))

(defn conn-open?
  "http-kit (2.8) doesn't call `on-close` when the client of a streaming
  HTTP response goes away and `open?` keeps returning true, so we also
  check the underlying selection key (not available in babashka)."
  [ch]
  (u/if-bb
   true
   (and (httpkit/open? ch)
        (if async-channel-key-field
          (.isValid ^java.nio.channels.SelectionKey (.get ^java.lang.reflect.Field async-channel-key-field ch))
          true))))

(defn reap-closed-conns! []
  (run! remove-conn! (remove conn-open? (keys @!conns))))

(def heartbeat-ms 15000)

(defn heartbeat! []
  (doseq [ch (keys @!conns)]
    (send-event! ch ":\n\n")))

(defn render-loop []
  (loop [next-prune 0]
    (when (try
            (if (.poll render-queue heartbeat-ms TimeUnit/MILLISECONDS)
              (do (Thread/sleep ^long render-tick-ms)
                  (.clear render-queue)
                  (reap-closed-conns!)
                  (render-all!))
              (do (reap-closed-conns!)
                  (heartbeat!)))
            true
            (catch InterruptedException _ false)
            (catch Throwable t
              (binding [*out* *err*] (println "Clerk render loop error:" (ex-message t)))
              true))
      (let [now (System/currentTimeMillis)]
        (if (< next-prune now)
          (do (prune-closed-tabs!) (recur (+ now 60000)))
          (recur next-prune))))))

(defonce !render-thread (atom nil))

(defn ensure-render-loop! []
  (when-not (some-> ^Thread @!render-thread .isAlive)
    (reset! !render-thread (doto (Thread. ^Runnable #(render-loop) "clerk-datastar-render")
                             (.setDaemon true)
                             (.start)))))

(defn state-changed [_key _ref old-state new-state]
  (when-not (identical? (:presented old-state) (:presented new-state))
    (swap! webserver/!state update :tabs prune-tabs (:presented new-state)))
  (request-render!))

(add-watch webserver/!state ::render #'state-changed)

(defn claim-tab-id!
  "Registers channel `ch` for `tab-id`.

  When another connection already uses that id, the newest connection
  wins and the older one is moved to a fresh id with a copy of the tab's
  state. On a reload the old connection is about to close anyway, for a
  duplicated tab or pasted url this keeps both tabs independent."
  [ch tab-id encoder]
  (doseq [[other-ch _] (filter #(= tab-id (:tab-id (val %))) @!conns)]
    (let [new-id (new-tab-id)]
      (swap! webserver/!state (fn [state]
                                (cond-> state
                                  (get-in state [:tabs tab-id]) (assoc-in [:tabs new-id] (get-in state [:tabs tab-id])))))
      (swap! !conns (fn [conns] (cond-> conns (contains? conns other-ch) (assoc-in [other-ch :tab-id] new-id))))
      (try (send-event! other-ch (script-event (replace-url-script new-id)))
           (catch Exception _))))
  (swap! !conns assoc ch {:tab-id tab-id :encoder encoder :nav-path (get-in @webserver/!state [:doc :nav-path])})
  (swap! !tab-last-seen assoc tab-id (System/currentTimeMillis)))

(defn sse-handler [{:as req :keys [query-string headers]}]
  (let [tab-id (get (webserver/query-string->map query-string) :tab)]
    (if-not (valid-tab-id? tab-id)
      {:status 400 :body "invalid tab id"}
      (let [encoder (negotiate-encoder (get headers "accept-encoding"))]
        (ensure-render-loop!)
        (httpkit/as-channel req
                            {:on-open (fn [ch]
                                        (httpkit/send! ch {:status 200
                                                           :headers (cond-> {"Content-Type" "text/event-stream"
                                                                             "Cache-Control" "no-store"}
                                                                      (:content-encoding encoder) (assoc "Content-Encoding" (:content-encoding encoder)))}
                                                       false)
                                        (claim-tab-id! ch tab-id encoder)
                                        (request-render!))
                             :on-close (fn [ch _status] (remove-conn! ch))})))))

;; ## Actions

(defn read-param [params k]
  (some-> (get params k) edn/read-string))

(defmulti handle-action (fn [action _tab-id _params] action))

(defmethod handle-action :default [action _ _]
  (throw (ex-info (str "Unknown action: " action) {:action action})))

(defmethod handle-action "toggle-expand" [_ tab-id params]
  (let [id (get params :id)
        path (read-param params :path)]
    (swap! webserver/!state update-result-state tab-id id #(update % :flipped toggle path))))

(defmethod handle-action "fetch-more" [_ tab-id params]
  (let [id (get params :id)
        elision (read-param params :elision)
        {:keys [presented blob-id]} (get (result-index (:presented @webserver/!state)) id)]
    (when-let [present-elision-fn (:present-elision-fn (meta presented))]
      (let [more (v/process-blobs {:blob-mode :lazy-load :blob-id blob-id} (present-elision-fn elision))]
        (swap! webserver/!state update-result-state tab-id id #(update % :fetched (fnil conj []) [elision more]))))))

(defn parse-number [s]
  (let [n (edn/read-string s)]
    (if (number? n)
      n
      (throw (ex-info (str "Not a number: " (pr-str s)) {:value s})))))

(defonce ^{:doc "Last sequence number of a sync action per `[tab-id var]`, to drop updates arriving out of order."}
  !sync-seqs
  (atom {}))

(defn newer-sync? [tab-id sym seq]
  (let [k [tab-id sym]
        [old _] (swap-vals! !sync-seqs (fn [seqs] (cond-> seqs (< (get seqs k -1) seq) (assoc k seq))))]
    (< (get old k -1) seq)))

(defmethod handle-action "sync" [_ tab-id params]
  (let [sym (read-param params :var)
        var (when (qualified-symbol? sym) (resolve sym))
        value (get params :value "")
        seq (some-> (get params :seq) (->> (re-matches #"\d{1,18}")) Long/parseLong)]
    (when-not (contains? (v/extract-sync-atom-vars (:doc @webserver/!state)) var)
      (throw (ex-info (str "`" sym "` is not a synced var of the current notebook.") {:var sym})))
    (when (or (nil? seq) (newer-sync? tab-id sym seq))
      (reset! @var (case (read-param params :coerce)
                     :number (parse-number value)
                     :string value)))))

(defmethod handle-action "eval" [_ _tab-id params]
  (binding [*ns* (or (:ns (:doc @webserver/!state)) (create-ns 'user))]
    (eval (read-param params :form)))
  (when (= "true" (:recompute params))
    (eval '(nextjournal.clerk/recompute!))))

(defmethod handle-action "navigate" [_ _tab-id {:keys [path]}]
  (webserver/navigate! {:nav-path path}))

(defn same-origin-datastar-request?
  "Actions can evaluate code so we only accept requests from Datastar
  (a custom header which cross-origin requests can't set without a CORS
  preflight) coming from the page's own origin."
  [{:keys [headers]}]
  (and (= "true" (get headers "datastar-request"))
       (if-let [origin (get headers "origin")]
         (= (str/replace origin #"^https?://" "") (get headers "host"))
         true)))

(def tab-less-actions #{"eval" "navigate"})

(defn action-handler [{:as req :keys [uri query-string request-method]}]
  (let [action (subs uri (count "/_clerk/action/"))
        {:as params :keys [tab]} (webserver/query-string->map query-string)]
    (cond
      (not= :post request-method) {:status 405}
      (not (same-origin-datastar-request? req)) {:status 403 :body "forbidden"}
      (not (or (valid-tab-id? tab) (tab-less-actions action))) {:status 400 :body "invalid tab id"}
      :else (try (handle-action action tab params)
                 {:status 204}
                 (catch Exception e
                   (binding [*out* *err*]
                     (println (str "Clerk action `" action "` failed: " (ex-message e))))
                   {:status 500 :body (ex-message e)})))))

;; ## Islands
;; The cljs viewer bundle running islands sends its messages (the same
;; ones it sends over the websocket otherwise) via HTTP POST.

(defmulti handle-island-message :type)

(defmethod handle-island-message :default [{:keys [type]}]
  {:status 400 :body (str "Unsupported message type: " type)})

(defmethod handle-island-message :eval [{:as msg :keys [form eval-id recompute?]}]
  (let [reply (try {:reply (binding [*ns* (or (:ns (:doc @webserver/!state)) (create-ns 'user))]
                             (eval form))}
                   (catch Exception e
                     {:error (Throwable->map (ex-info (str "An error occured during clerk-eval: " (ex-message e))
                                                      (dissoc msg :type :eval-id)
                                                      e))}))]
    (when recompute?
      (eval '(nextjournal.clerk/recompute!)))
    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body (v/->edn (merge {:type :eval-reply :eval-id eval-id} reply))}))

(defmethod handle-island-message :sync! [{:keys [var-name patch]}]
  (let [var (when (qualified-symbol? var-name) (resolve var-name))]
    (when-not (contains? (v/extract-sync-atom-vars (:doc @webserver/!state)) var)
      (throw (ex-info (str "`" var-name "` is not a synced var of the current notebook.") {:var var-name})))
    (swap! @var editscript/patch (editscript/edits->script patch))
    {:status 204}))

(defn island-handler [{:as req :keys [request-method body]}]
  (cond
    (not= :post request-method) {:status 405}
    (not (same-origin-datastar-request? req)) {:status 403 :body "forbidden"}
    :else (try (handle-island-message (binding [*read-eval* false]
                                        (webserver/read-msg (slurp body))))
               (catch Exception e
                 (binding [*out* *err*]
                   (println "Clerk island message failed:" (ex-message e)))
                 {:status 500 :body (ex-message e)}))))

(defn app [{:as req :keys [uri]}]
  (cond
    (= "/_clerk/sse" uri) (sse-handler req)
    (and (= "/_clerk/island" uri) (live-islands?)) (island-handler req)
    (= "/_clerk/components.js" uri) {:status 200
                                     :headers {"Content-Type" "text/javascript" "Cache-Control" "no-cache"}
                                     :body (slurp (io/resource "nextjournal/clerk/datastar/components.js"))}
    (str/starts-with? uri "/_clerk/action/") (action-handler req)
    :else {:status 404}))
