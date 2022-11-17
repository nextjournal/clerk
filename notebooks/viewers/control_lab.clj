;; # 🎛 Control Lab 🧑🏼‍🔬
(ns viewer.control-lab
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

;; Experimenting with ways of making controls. We start with two
;; little helper functions, one in sci and one in Clojure.

(def viewer-eval-viewer
  {:pred viewer/viewer-eval?
   :transform-fn (comp viewer/mark-presented
                       (viewer/update-val
                        (fn [x] (cond (viewer/viewer-eval? x) x
                                      (symbol? x) (viewer/->viewer-eval x)
                                      (var? x) (recur (symbol x))
                                      (viewer/var-from-def? x) (recur (:nextjournal.clerk/var-from-def x))))))
   :render-fn '(fn [x opts]
                 (if (satisfies? IDeref x) ;; special atoms handling to support reactivity
                   [nextjournal.clerk.render/render-tagged-value {:space? false}
                    "#object"
                    [nextjournal.clerk.render/inspect [(symbol (pr-str (type x))) @x]]]
                   [nextjournal.clerk.render/inspect x]))})

(clerk/add-viewers! [viewer-eval-viewer])

(viewer/->viewer-eval
 '(defn make-slider [opts]
    (fn [!state]
      [:input (merge {:type :range :value @!state :on-change #(reset! !state (int (.. % -target -value)))} opts)])))

(defn make-slider
  ([] (make-slider {}))
  ([opts] (assoc viewer-eval-viewer :render-fn (list 'make-slider opts))))

;; Let's go through the ways we can use this.

;; 1️⃣ On a var coming from a def
^{::clerk/sync true ::clerk/viewer (make-slider {})}
(defonce !num (atom 0))

;; 2️⃣ On a sharp quoted symbol (works with a fully qualified one as well, ofc).
(clerk/with-viewer (make-slider)
  `!num)

;; 3️⃣ On a var
^{::clerk/viewer (make-slider)}
#'!num

;; 4️⃣ On an explicit `ViewerEval` type
(clerk/with-viewer (make-slider)
  (viewer/->viewer-eval `!num))


#_ ;; TODO: plain (not quoted) symbol
^{::clerk/viewer (make-slider {})}
!num

;; We can customise the slider by passing different opts (that are merged).

(clerk/with-viewer (make-slider {:max 200})
  `!num)

;; Or use a completely custom `:render-fn`.
(clerk/with-viewer (assoc viewer-eval-viewer :render-fn '(fn [x] [:h2.bg-green-500.rounded-xl.text-center @x]))
  `!num)

(clerk/with-viewer viewer-eval-viewer
  `!num)

@!num

(viewer/->viewer-eval `!num)
