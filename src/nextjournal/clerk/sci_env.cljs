(ns nextjournal.clerk.sci-env
  (:refer-clojure :exclude [time])
  (:require-macros [nextjournal.clerk.render.macros :refer [sci-copy-nss]]
                   [nextjournal.clerk.sci-env :refer [def-cljs-core]])
  (:require ["@codemirror/language" :as codemirror-language]
            ["@codemirror/state" :as codemirror-state]
            ["@codemirror/view" :as codemirror-view]
            ["@lezer/highlight" :as lezer-highlight]
            ["@nextjournal/lang-clojure" :as lang-clojure]
            ["framer-motion" :as framer-motion]
            ["react" :as react]
            [applied-science.js-interop :as j]
            [cherry.compiler :as cherry]
            [cljs.reader]
            [cljs.math]
            [clojure.string :as str]
            [edamame.core :as edamame]
            [goog.object]
            [nextjournal.clerk.parser]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.render.code]
            [nextjournal.clerk.render.context :as view-context]
            [nextjournal.clerk.render.hooks]
            [nextjournal.clerk.render.navbar]
            [nextjournal.clerk.trim-image]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clojure-mode.commands]
            [nextjournal.clojure-mode.extensions.eval-region]
            [nextjournal.clojure-mode.keymap]
            [reagent.core :as reagent]
            [sci.configs.applied-science.js-interop :as sci.configs.js-interop]
            [sci.configs.reagent.reagent :as sci.configs.reagent]
            [sci.core :as sci]
            [sci.ctx-store]
            [shadow.esm]
            [reagent.debug :as d]
            [reagent.interop :as interop]
            [reagent.ratom]))

(set! js/globalThis.clerk #js {})
(set! js/globalThis.clerk.cljs_core #js {})
(def-cljs-core)
(j/assoc-in! js/globalThis [:reagent :core :atom] reagent/atom)
(j/assoc! js/globalThis :global_eval (fn [x]
                                       (js/eval.apply js/globalThis #js [x])))


(defn with-let-macro [_ _ bindings & body]
  (assert (vector? bindings)
          (str "with-let bindings must be a vector, not "
               (pr-str bindings)))
  (let [v (with-meta (gensym "with-let") {:tag 'clj})
        k (keyword v)
        init (gensym "init")
        ;; V is a reaction, which holds a JS array.
        ;; If the array is empty, initialize values and store to the
        ;; array, using binding index % 2 to access the array.
        ;; After init, the bindings are just bound to the values in the array.
        bs (into [init `(zero? (alength ~v))]
                 (map-indexed (fn [i x]
                                (if (even? i)
                                  x
                                  (let [j (quot i 2)]
                                    ;; Issue 525
                                    ;; If binding value is not yet set,
                                    ;; try setting it again. This should
                                    ;; also throw errors for each render
                                    ;; and prevent the body being called
                                    ;; if bindings throw errors.
                                    `(if (or ~init
                                             (not (.hasOwnProperty ~v ~j)))
                                       (interop/unchecked-aset ~v ~j ~x)
                                       (interop/unchecked-aget ~v ~j)))))
                              bindings))
        [forms destroy] (let [fin (last body)]
                          (if (and (list? fin)
                                   (= 'finally (first fin)))
                            [(butlast body) `(fn [] ~@(rest fin))]
                            [body nil]))
        add-destroy (when destroy
                      (list
                        `(let [destroy# ~destroy]
                           (if (reagent.ratom/reactive?)
                             (when (nil? (.-destroy ~v))
                               (set! (.-destroy ~v) destroy#))
                             (destroy#)))))
        asserting (if *assert* true false)
        res (gensym "res")]
    `(let [~v (reagent.ratom/with-let-values ~k)]
       ~(when asserting
          `(when-some [^clj c# reagent.ratom/*ratom-context*]
             (when (== (.-generation ~v) (.-ratomGeneration c#))
               (d/error "Warning: The same with-let is being used more "
                        "than once in the same reactive context."))
             (set! (.-generation ~v) (.-ratomGeneration c#))))
       (let ~(into bs [res `(do ~@forms)])
         ~@add-destroy
         ~res))))

(defn unchecked-aget-macro
  ([_ _ array idx]
   (list 'js* "(~{}[~{}])" array idx))
  ([_ _ array idx & idxs]
   (let [astr (apply str (repeat (count idxs) "[~{}]"))]
     `(~'js* ~(str "(~{}[~{}]" astr ")") ~array ~idx ~@idxs))))

(defn unchecked-aset-macro
  ([_ _ array idx val]
   (list 'js* "(~{}[~{}] = ~{})" array idx val))
  ([_ _ array idx idx2 & idxv]
   (let [n (dec (count idxv))
         astr (apply str (repeat n "[~{}]"))]
     `(~'js* ~(str "(~{}[~{}][~{}]" astr " = ~{})") ~array ~idx ~idx2 ~@idxv))))

(set! cherry/built-in-macros
      (assoc cherry/built-in-macros
             'reagent.core/with-let
             with-let-macro))

(set! cherry/built-in-macros
      (assoc cherry/built-in-macros
             'reagent.interop/unchecked-aget
             unchecked-aget-macro
             'reagent.interop/unchecked-aset
             unchecked-aset-macro))

;; (set! js/globalThis.clerk.cljs_core.keyword keyword) ;; hack for cherry
;; (set! js/globalThis.clerk.cljs_core.apply apply) ;; hack for cherry
;; (set! js/globalThis.clerk.cljs_core.inc inc) ;; hack for cherry
;; (set! js/globalThis.clerk.cljs_core.identity identity) ;; hack for cherry

(declare eval-form-cherry)

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

(defn ->viewer-fn-with-error-cherry [form]
  (try (binding [*eval* eval-form-cherry]
         (viewer/->viewer-fn form))
       (catch js/Error e
         (viewer/map->ViewerFn
          {:form form
           :f (fn [_]
                [render/error-view (ex-info (str "error in render-fn: " (.-message e)) {:render-fn form} e)])}))))

(defn ->viewer-eval-with-error [form]
  (try (*eval* form)
       (catch js/Error e
         (js/console.error "error in viewer-eval" e)
         (ex-info (str "error in viewer-eval: " (.-message e)) {:form form} e))))

(defn ->viewer-eval-with-error-cherry [form]
  (try (eval-form-cherry form)
       (catch js/Error e
         (js/console.error "error in viewer-eval" e)
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
           (or (get {'viewer-fn ->viewer-fn-with-error
                     'viewer-fn/cherry ->viewer-fn-with-error-cherry
                     'viewer-eval ->viewer-eval-with-error
                     'viewer-eval/cherry ->viewer-eval-with-error-cherry} tag)
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

(def ^{:doc "Stub implementation to be replaced during static site generation. Clerk is only serving one page currently."}
  doc-url
  (sci/new-var 'doc-url (fn [x] (str "#" x))))

(def viewer-namespace
  (merge (sci/copy-ns nextjournal.clerk.viewer (sci/create-ns 'nextjournal.clerk.viewer))
         {'html render/html-render
          'doc-url doc-url
          'url-for render/url-for
          'read-string read-string
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
                       'clojure.core {'read-string read-string
                                      'implements? (sci/copy-var implements?* core-ns)
                                      'time (sci/copy-var time core-ns)
                                      'system-time (sci/copy-var system-time core-ns)}}
                      (sci-copy-nss
                       'cljs.math
                       'nextjournal.clerk.parser
                       'nextjournal.clerk.render
                       'nextjournal.clerk.render.code
                       'nextjournal.clerk.render.hooks
                       'nextjournal.clerk.render.navbar

                       'nextjournal.clojure-mode.keymap
                       'nextjournal.clojure-mode.commands
                       'nextjournal.clojure-mode.extensions.eval-region)

                      sci.configs.js-interop/namespaces
                      sci.configs.reagent/namespaces)})

(defn ^:export onmessage [ws-msg]
  (render/dispatch (read-string (.-data ws-msg))))

(defn ^:export cherry-compile-string [s]
  (let [{:keys [body _imports]}
        (cherry/compile-string*
         s
         {:core-alias 'clerk.cljs_core
          :context :expression})]
    body))

(defn ^:export eval-form-cherry [f]
  (js/console.warn "compiling with cherry" (pr-str f))
  (let [{:keys [body _imports]}
        (cherry/compile-string*
         ;; function expression without name
         ;; isn't valid as top level JS form,
         ;; so we wrap it in a let
         (str f) #_(str/replace "(let [x %s] x)"
                      "%s"
                      (str f))
         {:core-alias 'clerk.cljs_core
          :context :expression})
        _ (prn "compiled body" body)
        evaled (js/global_eval body)
        _ (prn "evaled" evaled)]
    evaled))

(defn ^:export eval-form [f]
  (sci/eval-form (sci.ctx-store/get-ctx) f))

(defn ^:export set-state [state]
  (render/set-state! state))

(def ^:export mount render/mount)

(defn reconnect-timeout [failed-connection-attempts]
  (get [0 0 100 500 5000] failed-connection-attempts 10000))

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
