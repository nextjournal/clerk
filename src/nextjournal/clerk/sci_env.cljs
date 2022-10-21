(ns nextjournal.clerk.sci-env
  (:require ["framer-motion" :as framer-motion]
            [cljs.reader]
            [clojure.string :as str]
            [edamame.core :as edamame]
            [goog.object]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.viewer :as viewer :refer [code md plotly tex table vl row col with-viewer with-viewers]]
            [nextjournal.clerk.parser :as clerk.parser]
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
                 (with-viewer :tagged-value
                   {:tag tag
                    :space? (not (vector? value))
                    :value (cond-> value
                             (and (vector? value) (number? (second value)))
                             (update 1 (fn [memory-address]
                                         (with-viewer :number-hex memory-address))))}))))
         :features #{:clj}}))

(def ^:export set-state render/set-state)
(def ^:export mount render/mount)

(defn ^:export read-string [s]
  (edamame/parse-string s @!edamame-opts))


(def ^{:doc "Stub implementation to be replaced during static site generation. Clerk is only serving one page currently."}
  doc-url
  (sci/new-var 'doc-url (fn [x] (str "#" x))))

(def sci-viewer-namespace
  {'inspect-presented render/inspect-presented
   'inspect render/inspect
   'inspect-children render/inspect-children
   'set-viewers! render/set-viewers!
   'clerk-eval render/clerk-eval
   'consume-view-context view-context/consume
   'doc-url doc-url
   'url-for render/url-for
   'read-string read-string


   ;; clerk viewer API
   'code code
   'col col
   'html render/html-render
   'md md
   'plotly plotly
   'row row
   'table table
   'tex tex
   'vl vl
   'present viewer/present
   'mark-presented viewer/mark-presented
   'with-viewer with-viewer
   'with-viewers with-viewers
   'add-viewers viewer/add-viewers
   'update-val viewer/update-val})

(def render-namespace
  (sci/copy-ns nextjournal.clerk.render (sci/create-ns 'nextjournal.clerk.render)))

(defonce !sci-ctx
  (atom (sci/init {:async? true
                   :disable-arity-checks true
                   :classes {'js goog/global
                             'framer-motion framer-motion
                             :allow :all}
                   :aliases {'j 'applied-science.js-interop
                             'reagent 'reagent.core
                             'v 'nextjournal.clerk.sci-viewer
                             'p 'nextjournal.clerk.parser}
                   :namespaces (merge {'nextjournal.clerk.render render-namespace
                                       'nextjournal.clerk.sci-viewer sci-viewer-namespace
                                       'nextjournal.clerk.parser {'parse-clojure-string clerk.parser/parse-clojure-string
                                                                  'parse-markdown-string clerk.parser/parse-markdown-string}}
                                      sci.configs.js-interop/namespaces
                                      sci.configs.reagent/namespaces)})))

(defn ^:export eval-form [f]
  (sci/eval-form @!sci-ctx f))

(defn get-rgba [x y img-width img-data]
  (let [coord (* (+ (* img-width y) x) 4)]
    {:r (.at img-data coord)
     :g (.at img-data (+ coord 1))
     :b (.at img-data (+ coord 2))
     :a (.at img-data (+ coord 3))}))

(defn white? [x y img-width img-data]
  (= {:r 255 :g 255 :b 255 :a 255} (get-rgba x y img-width img-data)))

(defn scan-y [from-top? img-width img-height img-data]
  (loop [y (if from-top? 0 (dec img-height))
         colored-col nil]
    (if (and (not colored-col) (if from-top? (< y img-height) (< -1 y)))
      (recur
       (if from-top? (inc y) (dec y))
       (loop [x 0]
         (cond
           (not (white? x y img-width img-data)) y
           (< x (dec img-width)) (recur (inc x)))))
      colored-col)))

(defn scan-x [from-left? img-width img-height img-data]
  (loop [x (if from-left? 0 (dec img-width))
         colored-row nil]
    (if (and (not colored-row) (if from-left? (< x img-width) (<= 0 x)))
      (recur
       (if from-left? (inc x) (dec x))
       (loop [y 0]
         (cond
           (not (white? x y img-width img-data)) x
           (< y (dec img-height)) (recur (inc y)))))
      colored-row)))

(defn ^:export trim-image
  ([img] (trim-image img {}))
  ([img {:keys [padding] :or {padding 0}}]
   (let [canvas (js/document.createElement "canvas")
         ctx (.getContext canvas "2d")
         img-width (.-naturalWidth img)
         img-height (.-naturalHeight img)
         _ (.setAttribute canvas "width" img-width)
         _ (.setAttribute canvas "height" img-height)
         _ (.drawImage ctx img 0 0 img-width img-height)
         img-data (.-data (.getImageData ctx 0 0 img-width img-height))
         x1 (scan-x true img-width img-height img-data)
         y1 (scan-y true img-width img-height img-data)
         x2 (scan-x false img-width img-height img-data)
         y2 (scan-y false img-width img-height img-data)
         dx (inc (- x2 x1))
         dy (inc (- y2 y1))
         trimmed-data (.getImageData ctx x1 y1 dx dy)
         _ (.setAttribute canvas "width" (+ dx (* padding 2)))
         _ (.setAttribute canvas "height" (+ dy (* padding 2)))
         _ (.clearRect ctx 0 0 (+ dx padding) (+ dy padding))
         _ (set! (.-fillStyle ctx) "white")
         _ (.fillRect ctx 0 0 (.-width canvas) (.-height canvas))
         _ (.putImageData ctx trimmed-data padding padding)
         result-img (js/document.createElement "img")]
     (.setAttribute result-img "src" (.toDataURL canvas "image/png"))
     result-img)))

(defn ^:export append-trimmed-image [base64 id]
  (let [img (js/document.createElement "img")]
    (.addEventListener img "load" (fn [event]
                                    (let [trimmed-img (trim-image (.-target event) {:padding 20})]
                                      (.setAttribute trimmed-img "id" id)
                                      (.. js/document -body (appendChild trimmed-img)))))
    (.setAttribute img "src" base64)))

(set! *eval* eval-form)
