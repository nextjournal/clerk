;; # ⚡️ Clerk Sync and Controls 🎛

(ns viewers.controls
  "Demo of Clerk's two-way bindings."
  {:nextjournal.clerk/visibility {:code :show :result :show}}
  (:require [clojure.core :as core]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

;; We `defonce` an atom and tag it with `^::clerk/sync`. This will create a corresponding (reagent) atom in the browser.
^{::clerk/sync true}
(defonce number-atom
  (atom 0))

^::clerk/sync
(defonce name-atom
  (atom "Sam Gold"))


;; This is showing the state that the JVM has.
@number-atom

@name-atom

#'number-atom

#'name-atom

;; # 1️⃣ `comp` `:render-fn`

(def transform-var
  (comp clerk/mark-presented
        (clerk/update-val (fn [v] (viewer/->ViewerEval (list 'resolve (list 'quote (symbol v))))))))

(def render-slider
  '(fn [state-atom]
     [:input {:type :range :value @state-atom :on-change #(swap! state-atom (constantly (int (.. % -target -value))))}]))

(def render-text-input
  '(fn [state-atom]
     [:input {:type :text :value @state-atom :on-change #(swap! state-atom (constantly (.. % -target -value)))
              :class "px-3 py-3 placeholder-blueGray-300 text-blueGray-600 relative bg-white bg-white rounded text-sm border border-blueGray-300 outline-none focus:outline-none focus:ring w-full"}]))


^{::clerk/viewer {:render-fn (list 'comp render-slider 'deref) :transform-fn transform-var}}
#'number-atom

@number-atom

^{::clerk/viewer {:render-fn (list 'comp render-text-input 'deref) :transform-fn transform-var}}
#'name-atom

@name-atom


;; ## ⁉️ Sidequest: inspect atom

^{::clerk/viewer {:transform-fn transform-var
                  :render-fn '#(vector nextjournal.clerk.render/inspect @%)}}
#'number-atom

;; ✅ Fixed with `7539cc2dfc16682cc17203fd4b7a096a6827f77c`


;; # 2️⃣ `::clerk/viewers`


@number-atom

(def var-viewer
  {:pred var?
   :transform-fn transform-var
   :render-fn '(fn [x] [nextjournal.clerk.render/inspect @x])})


^{::clerk/viewers (clerk/add-viewers [(assoc var-viewer :render-fn (list 'comp render-text-input 'deref))])}
#'name-atom

@name-atom

;; It might be more convenient to have a viewer that works on vars from defs and normal vars.

(def convenient-slider
  {:transform-fn (comp transform-var (clerk/update-val #(cond-> % (viewer/get-safe % ::clerk/var-from-def) ::clerk/var-from-def)))
   :render-fn '(fn [x] (let [state-atom (cond-> x (var? x) deref)]
                         [:input {:type :range :value @state-atom :on-change #(swap! state-atom (constantly (int (.. % -target -value))))}]))})

;; But this should probably be fixed in a principled way.


^{::clerk/viewer convenient-slider ::clerk/sync true}
(defonce number-atom-2 (atom 99))

^{::clerk/viewer convenient-slider}
#'number-atom-2

@number-atom-2
