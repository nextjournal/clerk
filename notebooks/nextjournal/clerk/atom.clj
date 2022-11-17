(ns nextjournal.clerk.atom
  "Demo of Clerk's two-way bindings."
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.core :as core]
            [nextjournal.clerk :as clerk]))

(def transform-var
  (comp (clerk/update-val symbol)
        clerk/mark-presented))

(def counter-viewer
  {:transform-fn transform-var
   :render-fn '(fn [var-name]
                 (if-let [var (resolve var-name)]
                   (let [atom @var]
                     [:div
                      [:h2 "Counter Example"]
                      [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(swap! atom update :counter inc)} "+"]
                      [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(swap! atom update :counter dec)} "-"]
                      [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(swap! atom (fn [_] {:counter 0}))} "reset"]
                      [nextjournal.clerk.render/inspect @atom]])
                   [:div "could not resolve" var-name]))})

(def slider-viewer
  {:render-fn '(fn [x]
                 [:div
                 [:input {:type      :range
                          :value     (:counter @@(resolve x))
                          :on-change #(swap! @(resolve x)
                                             assoc
                                             :counter
                                             (int (.. % -target -value)))}]])
   :transform-fn transform-var})

{::clerk/visibility {:code :show :result :show}}

;; # 🧮 Counter in Clerk

;; We `defonce` an atom and tag it with `^::clerk/sync`. This will create a corresponding (reagent) atom in the browser.
^::clerk/sync
(defonce my-state
  (atom {:counter 0}))


;; This is showing the state that the JVM has.
@my-state

^{::clerk/viewer counter-viewer}
#'my-state

^{::clerk/viewer slider-viewer}
`my-state


;; changing my-state on the JVM and running clerk/show! will update the slider
;; and counter accordingly:
(comment
  (do
    (swap! my-state assoc :counter 20)
    (clerk/show! *ns*))
  )
