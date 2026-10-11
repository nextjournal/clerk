(ns nextjournal.clerk.datastar
  "Datastar frontend, enabled via `(clerk/serve! {:frontend :datastar})`.

  The page is a pure function of `webserver/!state` and a tab id. On every
  state change a render loop re-renders `<main id=\"clerk\">` for each
  connected tab and pushes it over a compressed SSE stream, Datastar then
  morphs it into the DOM. Actions only update state and return 204.

  Per tab ui state lives in `(:tabs @webserver/!state)`, keyed by a tab id
  that's kept in the `tab` query param so it survives reloads."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [hiccup.core :as hiccup1]
            [hiccup.page]
            [hiccup2.core :as h]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.html :as html]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver]
            [org.httpkit.server :as httpkit])
  (:import (java.io ByteArrayOutputStream)
           (java.nio.channels SelectionKey)
           (java.nio.charset StandardCharsets)
           (java.util.concurrent ExecutorService LinkedBlockingQueue TimeUnit)
           (java.util.zip GZIPOutputStream)
           (org.httpkit.server AsyncChannel)))

(def datastar-url "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.4/bundles/datastar.js")

;; ## Tab state

(defn new-tab-id []
  (subs (str (random-uuid)) 0 8))

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
    (update-vals tabs #(prune-tab % index))))

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

(defn action-attrs [tab-id action params]
  {:data-on:click (str "@post('/_clerk/action/" (name action) "?" (query-string (assoc params :tab tab-id)) "')")})

(defn result-state-fn [tab]
  (fn [id presented auto-expand?]
    (let [{:keys [flipped fetched]} (get-in tab [:results id])
          presented' (reduce (fn [p [elision more]] (v/merge-presentations p more elision)) presented fetched)]
      {:presented presented'
       :expanded-at (reduce #(update %1 %2 not) (html/->expanded-at auto-expand? presented') flipped)})))

(defn render-ctx [tab-id tab]
  {:result-state (result-state-fn tab)
   :on-toggle (fn [{:keys [result-id]} path]
                (action-attrs tab-id :toggle-expand {:id result-id :path path}))
   :on-fetch (fn [{:keys [result-id]} elision]
               (action-attrs tab-id :fetch-more {:id result-id :elision elision}))})

(defn render-main [{:keys [presented status tabs]} tab-id]
  [:main#clerk
   (when status (html/render-status status))
   (when presented
     (html/inspect (render-ctx tab-id (get tabs tab-id)) presented))])

(defn replace-url-script [tab-id]
  (str "{const u = new URL(location); u.searchParams.set('tab', '" tab-id "'); history.replaceState(history.state, '', u)}"))

(def page-css
  ".cm-line { padding: 0; line-height: 1.6; font-size: 15px; font-family: \"Fira Mono\", monospace; }")

(defn page-html [{:as state :keys [presented]} tab-id {:keys [replace-url?]}]
  (str "<!DOCTYPE html>\n"
       (h/html {:mode :html}
               [:html
                [:head
                 [:meta {:charset "UTF-8"}]
                 [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                 [:title (or (-> presented :nextjournal/value :title) "Clerk")]
                 (h/raw (hiccup1/html (view/include-viewer-css {:resource->url @config/!resource->url})))
                 (h/raw (hiccup1/html (hiccup.page/include-css "https://fonts.bunny.net/css?family=fira-mono:400,700%7Cfira-sans:400,400i,500,500i,700,700i%7Cfira-sans-condensed:700,700i%7Cpt-serif:400,400i,700,700i")))
                 [:style (h/raw page-css)]
                 [:script {:type "module" :src datastar-url}]]
                [:body.dark:bg-gray-900
                 {:data-init (str "@get('/_clerk/sse?tab=" tab-id "', {openWhenHidden: true, retryMaxCount: Infinity})")}
                 (when replace-url?
                   [:script (h/raw (replace-url-script tab-id))])
                 (render-main state tab-id)]])))

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
  (when-let [{:keys [tab-id last-inputs]} (get @!conns ch)]
    (let [inputs (render-inputs state tab-id)]
      (when-not (and last-inputs (every? true? (map identical? inputs last-inputs)))
        (send-event! ch (sse-event (html/->html-str (render-main state tab-id))))
        (swap! !conns (fn [conns] (cond-> conns (contains? conns ch) (assoc-in [ch :last-inputs] inputs))))))))

(defn virtual-thread-executor
  "Returns a virtual thread per task executor when running on JDK 21+, nil otherwise."
  ^ExecutorService []
  (try (clojure.lang.Reflector/invokeStaticMethod java.util.concurrent.Executors "newVirtualThreadPerTaskExecutor" (object-array 0))
       (catch Throwable _ nil)))

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

(defn prune-closed-tabs! []
  (let [live (into #{} (map :tab-id) (vals @!conns))
        now (System/currentTimeMillis)
        stale? (fn [tab-id] (and (not (live tab-id))
                                 (< tab-grace-ms (- now (get @!tab-last-seen tab-id 0)))))]
    (when (some stale? (keys (:tabs @webserver/!state)))
      (swap! webserver/!state update :tabs #(into {} (remove (comp stale? key)) %))
      (swap! !tab-last-seen #(into {} (remove (comp stale? key)) %)))))

(defn remove-conn! [ch]
  (when-let [{:keys [tab-id encoder]} (get @!conns ch)]
    (swap! !conns dissoc ch)
    (swap! !tab-last-seen assoc tab-id (System/currentTimeMillis))
    ((:close encoder))))

(def ^:private ^java.lang.reflect.Field async-channel-key-field
  (try (doto (.getDeclaredField AsyncChannel "key") (.setAccessible true))
       (catch Exception _ nil)))

(defn conn-open?
  "http-kit (2.8) doesn't call `on-close` when the client of a streaming
  HTTP response goes away and `open?` keeps returning true, so we also
  check the underlying selection key."
  [ch]
  (and (httpkit/open? ch)
       (if async-channel-key-field
         (.isValid ^SelectionKey (.get async-channel-key-field ch))
         true)))

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
  (swap! !conns assoc ch {:tab-id tab-id :encoder encoder})
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

(defn action-handler [{:keys [uri query-string request-method]}]
  (if (not= :post request-method)
    {:status 405}
    (let [action (subs uri (count "/_clerk/action/"))
          {:as params :keys [tab]} (webserver/query-string->map query-string)]
      (if (valid-tab-id? tab)
        (do (handle-action action tab params)
            {:status 204})
        {:status 400 :body "invalid tab id"}))))

(defn app [{:as req :keys [uri]}]
  (cond
    (= "/_clerk/sse" uri) (sse-handler req)
    (str/starts-with? uri "/_clerk/action/") (action-handler req)
    :else {:status 404}))
