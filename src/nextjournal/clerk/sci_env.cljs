(ns nextjournal.clerk.sci-env
  (:require ["@codemirror/view" :as codemirror-view]
            ["@codemirror/state" :as codemirror-state]
            ["framer-motion" :as framer-motion]
            [cljs.reader]
            [clojure.string :as str]
            [edamame.core :as edamame]
            [goog.object]
            [applied-science.js-interop :as j]
            [nextjournal.clerk.parser]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.render.code]
            [nextjournal.clerk.render.hooks]
            [nextjournal.clerk.trim-image]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.view.context :as view-context]
            [sci.configs.applied-science.js-interop :as sci.configs.js-interop]
            [sci.configs.reagent.reagent :as sci.configs.reagent]
            [sci.core :as sci]
            [sci.ctx-store]))

(defn ->viewer-fn-with-error [form]
  (try (viewer/->viewer-fn form)
       (catch js/Error e
         (try (eval form)
              (catch js/Error e
                (fn [_]
                  [render/error-view (ex-info (str "error in render-fn: " (.-message e)) {:render-fn form} e)]))))))

(defn ->viewer-eval-with-error [form]
  (try (*eval* form)
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
           (or (get {'viewer-fn   ->viewer-fn-with-error
                     'viewer-eval ->viewer-eval-with-error} tag)
               (fn [value]
                 (viewer/with-viewer :tagged-value
                   {:tag tag
                    :space? (not (vector? value))
                    :value (cond-> value
                             (and (vector? value) (number? (second value)))
                             (update 1 (fn [memory-address]
                                         (viewer/with-viewer :number-hex memory-address))))}))))
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

(def render-namespace
  (sci/copy-ns nextjournal.clerk.render (sci/create-ns 'nextjournal.clerk.render)))

(def parser-namespace
  (sci/copy-ns nextjournal.clerk.parser (sci/create-ns 'nextjournal.clerk.parser)))

(def hooks-namespace
  (sci/copy-ns nextjournal.clerk.render.hooks (sci/create-ns 'nextjournal.clerk.render.hooks)))

(def code-namespace
  (sci/copy-ns nextjournal.clerk.render.code (sci/create-ns 'nextjournal.clerk.render.code)))

;; classes which cannot be resolved by symbol
(def libname->class
  {"@codemirror/view" codemirror-view
   "@codemirror/state" codemirror-state})

(defn load-fn [{:keys [libname namespace ctx opts ns]}]
  (js/console.log :lib libname )
  (when (contains? libname->class libname)
    (let [{:keys [as refer]} opts
          munged-libname (symbol (munge libname))
          klass (libname->class libname)]
      (sci/add-class! ctx munged-libname klass)
      (when as
        (sci/add-import! ctx ns munged-libname as))
      (doseq [r refer]
        (when-some [prop (j/get klass (name r))]
          (let [sub-libname (str munged-libname "$" r)]
            (sci/add-class! ctx sub-libname prop)
            (sci/add-import! ctx ns sub-libname r))))
      {:handled true})))

(def initial-sci-opts
  {:async? true
   :load-fn load-fn
   :disable-arity-checks true
   :classes {'js goog/global
             'framer-motion framer-motion
             :allow :all}
   :aliases {'j 'applied-science.js-interop
             'reagent 'reagent.core
             'v 'nextjournal.clerk.viewer
             'p 'nextjournal.clerk.parser}
   :namespaces (merge {'nextjournal.clerk.render render-namespace
                       'nextjournal.clerk.render.hooks hooks-namespace
                       'nextjournal.clerk.render.code code-namespace
                       'nextjournal.clerk.viewer viewer-namespace
                       'nextjournal.clerk.parser parser-namespace
                       'clojure.core {'read-string read-string}}
                      sci.configs.js-interop/namespaces
                      sci.configs.reagent/namespaces)})

(defn ^:export onmessage [ws-msg]
  (render/dispatch (read-string (.-data ws-msg))))

(defn ^:export eval-form [f]
  (sci/eval-form (sci.ctx-store/get-ctx) f))

(defn ^:export set-state [state]
  (render/set-state! state))

(def ^:export mount render/mount)

(sci.ctx-store/reset-ctx! (sci/init initial-sci-opts))

(sci/alter-var-root sci/print-fn (constantly *print-fn*))
(sci/alter-var-root sci/print-err-fn (constantly *print-err-fn*))

(set! *eval* eval-form)
