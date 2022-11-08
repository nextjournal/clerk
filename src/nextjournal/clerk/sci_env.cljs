(ns nextjournal.clerk.sci-env
  (:require ["framer-motion" :as framer-motion]
            [cljs.reader]
            [clojure.string :as str]
            [edamame.core :as edamame]
            [goog.object]
            [nextjournal.clerk.parser]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.trim-image]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.view.context :as view-context]
            [sci.configs.applied-science.js-interop :as sci.configs.js-interop]
            [sci.configs.reagent.reagent :as sci.configs.reagent]
            [sci.core :as sci]))


(defn eval-viewer-fn [eval-f form]
  (try (eval-f form)
       (catch js/Error e
         (throw (ex-info (str "error in render-fn: " (.-message e)) {:render-fn form} e)))))

(defonce !edamame-opts
  (atom {:all true
         :row-key :line
         :col-key :column
         :location? seq?
         :end-location false
         :read-cond :allow
         :readers
         (fn [tag]
           (or (get {'viewer-fn   (partial eval-viewer-fn viewer/->viewer-fn)
                     'viewer-eval (partial eval-viewer-fn *eval*)} tag)
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

(defonce !sci-ctx
  (atom (sci/init {:async? true
                   :disable-arity-checks true
                   :classes {'js goog/global
                             'framer-motion framer-motion
                             :allow :all}
                   :aliases {'j 'applied-science.js-interop
                             'reagent 'reagent.core
                             'v 'nextjournal.clerk.viewer
                             'p 'nextjournal.clerk.parser}
                   :namespaces (merge {'nextjournal.clerk.render render-namespace
                                       'nextjournal.clerk.viewer viewer-namespace
                                       'nextjournal.clerk.parser parser-namespace
                                       'clojure.core {'swap! nextjournal.clerk.render/clerk-swap!}}
                                      sci.configs.js-interop/namespaces
                                      sci.configs.reagent/namespaces)})))

(defn ^:export onmessage [ws-msg]
  (render/dispatch (assoc (read-string (.-data ws-msg)) :sci-ctx @!sci-ctx)))

(defn ^:export eval-form [f]
  (sci/eval-form @!sci-ctx f))

(defn ^:export set-state [state]
  (render/set-state! (assoc state :sci-ctx @!sci-ctx)))

(def ^:export mount render/mount)


(set! *eval* eval-form)

(sci/alter-var-root sci/print-fn (constantly *print-fn*))
(sci/alter-var-root sci/print-err-fn (constantly *print-err-fn*))
