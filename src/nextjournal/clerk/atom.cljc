(ns nextjournal.clerk.atom
  "Clerks two-way bindings."
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.core :as core]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

(def atom-viewer
  {:transform-fn (comp clerk/mark-presented
                       (clerk/update-val (fn [{:nextjournal.clerk/keys [var-from-def]}]
                                           (symbol var-from-def))))
   :render-fn '(fn [var-name {:as opts :keys [render-fn]}]
                 (reagent/with-let [var (resolve var-name)]
                   [:div "☯️ " [nextjournal.clerk.render/inspect @@var]]))})

(def counter-viewer
  {:transform-fn (comp clerk/mark-presented
                       (clerk/update-val symbol))
   :render-fn '(fn [var-name]
                 (if-let [var (resolve var-name)]
                   (let [atom @var]
                     [:div
                      [:h2 "Counter Example"]
                      [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(nextjournal.clerk.render/swap-fn! atom update :counter inc)} "+"]
                      [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(nextjournal.clerk.render/swap-fn! atom update :counter dec)} "-"]
                      [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(nextjournal.clerk.render/swap-fn! atom (fn [_] {:counter 0}))} "reset"]
                      [nextjournal.clerk.render/inspect @atom]])
                   [:div "could not resolve" var-name]))})

(def atom-inspector
  {:transform-fn (clerk/update-val symbol)
   :render-fn '(fn [x] [nextjournal.clerk.render/inspect x])})

{::clerk/visibility {:code :show :result :show}}

;; # 🧮 Counter in Clerk

;; We `defonce` an atom and show it using the `atom-viewer`. This will create a corresponding (reagent) atom in the browser.
^{::clerk/viewer atom-viewer ::clerk/visibility {:result :show}}
(defonce ^::clerk/sync my-state
  (atom {:counter 0}))

;; This is showing the state that the JVM has.
@my-state

^{::clerk/viewer counter-viewer}
#'my-state



#_(-> (atom {}) meta)


