(ns nextjournal.clerk.sci-env
  (:refer-clojure :exclude [time])
  (:require-macros [nextjournal.clerk.render.macros :refer [sci-copy-nss]])
  (:require ["@codemirror/language" :as codemirror-language]
            ["@codemirror/state" :as codemirror-state]
            ["@codemirror/view" :as codemirror-view]
            ["@lezer/highlight" :as lezer-highlight]
            ["@nextjournal/lang-clojure" :as lang-clojure]
            ["framer-motion" :as framer-motion]
            ["react" :as react]
            [applied-science.js-interop :as j]
            [cljs.math]
            [cljs.reader]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [edamame.core :as edamame]
            [goog.object]
            [nextjournal.clerk.cherry-env :as cherry-env]
            [nextjournal.clerk.parser]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.render.code]
            [nextjournal.clerk.render.context :as view-context]
            [nextjournal.clerk.render.editor]
            [nextjournal.clerk.render.hooks]
            [nextjournal.clerk.render.navbar]
            [nextjournal.clerk.trim-image]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clojure-mode.commands]
            [nextjournal.clojure-mode.extensions.eval-region]
            [nextjournal.clojure-mode.keymap]
            [nextjournal.clerk.sci-env.nrepl :as nrepl]
            [reagent.dom.server :as dom-server]
            [reagent.ratom :as ratom]
            [sci.configs.applied-science.js-interop :as sci.configs.js-interop]
            [sci.configs.reagent.reagent :as sci.configs.reagent]
            [sci.core :as sci]
            [sci.ctx-store]
            [shadow.esm]))

(def legacy-ns-aliases
  {"j" "applied-science.js-interop"
   "reagent" "reagent.core"
   "render" "nextjournal.clerk.render"
   "v" "nextjournal.clerk.viewer"
   "p" "nextjournal.clerk.parser"})

(defn resolve-legacy-alias [sym]
  (symbol (get legacy-ns-aliases (namespace sym) (namespace sym))
          (name sym)))

(defn maybe-handle-legacy-alias-error [form e]
  (when-let [unresolved-sym (some-> (re-find #"^Could not resolve symbol: (.*)$" (ex-message e)) second symbol)]
    (when (and (contains? legacy-ns-aliases (namespace unresolved-sym))
               (sci/resolve (sci.ctx-store/get-ctx) (resolve-legacy-alias unresolved-sym)))
      (viewer/map->ViewerFn
       {:form form
        :f (fn [] [render/error-view (ex-info (str "We now require `:render-fn`s to use fully-qualified symbols, and we have removed the old aliases from Clerk. "
                                                   "Please change `" unresolved-sym "` to `" (resolve-legacy-alias unresolved-sym) "` in your `:render-fn` to resolve this issue.")
                                              {:render-fn form} e)])}))))

(defn ->viewer-fn-with-error [form]
  (try (viewer/->viewer-fn form)
       (catch js/Error e
         (or (maybe-handle-legacy-alias-error form e)
             (viewer/map->ViewerFn
              {:form form
               :f (fn [_]
                    [render/error-view (ex-info (str "error in render-fn: " (.-message e)) {:render-fn form} e)])})))))

(defn ->viewer-eval-with-error [form]
  (try (*eval* form)
       (catch js/Error e
         (js/console.error "error in viewer-eval" e form)
         (ex-info (str "error in viewer-eval: " (.-message e)) {:form form} e))))

(defonce !edamame-opts
  (atom {:all true
         :row-key :line
         :col-key :column
         :location? seq?
         :end-location false
         :read-cond :allow
         :readers
         (fn [tag]
           (or (get @cljs.reader/*tag-table* tag)
               (get {'viewer-fn ->viewer-fn-with-error
                     'viewer-fn/cherry cherry-env/->viewer-fn-with-error
                     'viewer-eval ->viewer-eval-with-error
                     'viewer-eval/cherry cherry-env/->viewer-eval-with-error} tag)
               (fn [value]
                 (viewer/with-viewer `viewer/tagged-value-viewer
                   {:tag tag
                    :space? (not (vector? value))
                    :value (cond-> value
                             (and (vector? value) (number? (second value)))
                             (update 1 (fn [memory-address]
                                         (viewer/with-viewer `viewer/number-hex-viewer memory-address))))}))))
         :features #{:clj}}))

(defn ^:export read-string [s]
  (edamame/parse-string s @!edamame-opts))

(defn read-string-without-tag-table [s]
  (binding [cljs.reader/*tag-table* (atom {})]
    (edamame/parse-string s @!edamame-opts)))

(def ^{:doc "Stub implementation to be replaced during static site generation. Clerk is only serving one page currently."}
  doc-url (sci/new-var 'doc-url viewer/doc-url))

(defn ^:private render-html-or-viewer [x]
  ;; We've dropped the need to write `nextjournal.clerk.viewer/html` in `:render-fn`s in 0.12, see
  ;; https://github.com/nextjournal/clerk/blob/62b91b7e5a4487472129ea41095de6c62e8834ce/CHANGELOG.md#012699-2022-12-02

  ;; If we don't override `nextjournal.clerk.viewer/html` for the sci
  ;; env, we'd produce an infinte loop in the browser. So we're
  ;; instead checking if we're inside a reactive context and only
  ;; calling `render-html` in that case. Otherwise (i.e. in
  ;; `notebooks/cards.clj` we call the normal viewer fn.
  (if ratom/*ratom-context*
    (render/render-html x)
    (viewer/html x)))

(def viewer-namespace
  (merge (sci/copy-ns nextjournal.clerk.viewer (sci/create-ns 'nextjournal.clerk.viewer))
         {'html render-html-or-viewer
          'doc-url doc-url
          'url-for render/url-for
          'read-string read-string
          'read-string-without-tag-table read-string-without-tag-table
          'clerk-eval render/clerk-eval
          'consume-view-context view-context/consume
          'inspect-presented render/inspect-presented
          'inspect render/inspect
          'inspect-children render/inspect-children
          'set-viewers! render/set-viewers!
          'with-d3-require render/with-d3-require}))

(defn ^:macro implements?* [_ _ psym x]
  ;; hardcoded implementation of implements? for js-interop destructure which
  ;; uses implements?
  (case psym
    cljs.core/ISeq (implements? ISeq x)
    cljs.core/INamed (implements? INamed x)
    (list 'cljs.core/instance? psym x)))

(def core-ns (sci/create-ns 'clojure.core nil))

(defn ^:sci/macro time [_ _ expr]
  `(let [start# (system-time)
         ret# ~expr]
     (prn (cljs.core/str "Elapsed time: "
                         (.toFixed (- (system-time) start#) 6)
                         " msecs"))
     ret#))

(def initial-sci-opts
  {:classes {'js (j/assoc! goog/global "import" shadow.esm/dynamic-import)
             'framer-motion framer-motion
             :allow :all}
   :js-libs {"@codemirror/language" codemirror-language
             "@codemirror/state" codemirror-state
             "@codemirror/view" codemirror-view
             "@lezer/highlight" lezer-highlight
             "@nextjournal/lang-clojure" lang-clojure
             "framer-motion" framer-motion
             "react" react}
   :ns-aliases '{clojure.math cljs.math}
   :namespaces (merge {'nextjournal.clerk.viewer viewer-namespace
                       'nextjournal.clerk viewer-namespace ;; TODO: expose cljs variant of `nextjournal.clerk` with docstrings
                       'clojure.core {'read-string read-string
                                      'implements? (sci/copy-var implements?* core-ns)
                                      'time (sci/copy-var time core-ns)
                                      'system-time (sci/copy-var system-time core-ns)}}
                      (sci-copy-nss
                       'cljs.math
                       'nextjournal.clerk.parser
                       'nextjournal.clerk.render
                       'nextjournal.clerk.render.code
                       'nextjournal.clerk.render.editor
                       'nextjournal.clerk.render.hooks
                       'nextjournal.clerk.render.navbar

                       'nextjournal.clojure-mode.keymap
                       'nextjournal.clojure-mode.commands
                       'nextjournal.clojure-mode.extensions.eval-region)

                      sci.configs.js-interop/namespaces
                      sci.configs.reagent/namespaces)})

(defn ^:export onmessage [ws-msg]
  (render/dispatch (read-string (.-data ws-msg))))

(defn ^:export eval-form [f]
  (sci/eval-form (sci.ctx-store/get-ctx) f))

(def ^:export init render/init)

(defn ^:export ssr [state-str]
  (init (read-string state-str))
  (dom-server/render-to-string [render/root]))

(defn reconnect-timeout [failed-connection-attempts]
  (get [0 0 100 500 5000] failed-connection-attempts 10000))

(defn ^:export connect-render-nrepl [ws-url]
  (let [ws (js/WebSocket. ws-url)]
    (prn :connect-render-nrepl ws-url)
    (set! (.-ws_nrepl js/window) ws)
    (set! (.-onmessage ws)
          (fn [event]
            (nrepl/handle-nrepl-message (edn/read-string (.-data event)))))
    (set! (.-onerror ws)
          (fn [event]
            (js/console.log event)))))

(defn ^:export connect [ws-url]
  (when (::failed-attempts @render/!doc)
    (swap! render/!doc assoc ::connection-status "Reconnecting…"))
  (let [ws (js/WebSocket. ws-url)]
    (set! (.-onmessage ws) onmessage)
    (set! (.-onopen ws) (fn [e] (swap! render/!doc dissoc ::connection-status ::failed-attempts)))
    (set! (.-onclose ws) (fn [e]
                           (let [timeout (reconnect-timeout (::failed-attempts @render/!doc 0))]
                             (swap! render/!doc
                                    (fn [doc]
                                      (-> doc
                                          (assoc ::connection-status (if (pos? timeout)
                                                                       (str "Disconnected, reconnecting in " timeout "ms…")
                                                                       "Reconnecting…"))
                                          (update ::failed-attempts (fnil inc 0)))))
                             (js/setTimeout #(connect ws-url) timeout))))
    (set! (.-clerk_ws ^js goog/global) ws)
    (set! (.-ws_send ^js goog/global) (fn [msg] (.send ws msg)))))

(sci.ctx-store/reset-ctx! (sci/init initial-sci-opts))

(sci/enable-unrestricted-access!)

(sci/alter-var-root sci/print-fn (constantly *print-fn*))
(sci/alter-var-root sci/print-err-fn (constantly *print-err-fn*))

(set! *eval* eval-form)
