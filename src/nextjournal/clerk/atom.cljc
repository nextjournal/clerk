(ns nextjournal.clerk.atom
  "Clerks two-way bindings."
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.core :as core]
            [nextjournal.clerk :as clerk]))
(def atom-viewer
  {:transform-fn (comp clerk/mark-presented
                       (clerk/update-val (fn [{:nextjournal.clerk/keys [var-from-def]}]
                                           {:var-name (symbol var-from-def) :state @@var-from-def})))
   :render-fn '(fn [{:keys [var-name state]} {:as opts :keys [render-fn]}]
                 (reagent/with-let [var (or (resolve var-name)
                                            (do (js/console.log (pr-str (list 'defatom var-name (list 'atom state))))
                                                (intern (create-ns (symbol (namespace var-name)))
                                                        (symbol (name var-name))
                                                        (with-meta (reagent/atom state)
                                                          {:var-name var-name}))))
                                    render-fn' (eval (second render-fn))]
                   (js/console.log (pr-str :render-fn render-fn :render-fn' render-fn'))
                   (or (when render-fn [render-fn' @var])
                       [:div "☯️☯️☯️ " [nextjournal.clerk.render/inspect @@var]])))})

(def counter-viewer
  {:transform-fn (comp (clerk/update-val symbol)
                       clerk/mark-presented)
   :render-fn '(fn [var-name]
                 (let [atom @(resolve var-name)]
                   [:div
                    [:h2 "Counter Example"]
                    [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(nextjournal.clerk.render/swap-fn! atom update :counter inc)} "+"]
                    [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(nextjournal.clerk.render/swap-fn! atom update :counter dec)} "-"]
                    [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(nextjournal.clerk.render/swap-fn! atom (fn [_] {:counter 0}))} "reset"]
                    [nextjournal.clerk.render/inspect @atom]]))})

(def atom-inspector
  {:transform-fn (clerk/update-val symbol)
   :render-fn '(fn [x] [nextjournal.clerk.render/inspect x])})

(defn atom
  [initial-state]
  (core/atom initial-state))


{:nextjournal.clerk/visibility {:code :show :result :show}}

;; # 🧮 Counter in Clerk

;; We `defonce` an atom and show it using the `atom-viewer`. This will create a corresponding (reagent) atom in the browser.
^{::clerk/viewer atom-viewer
  ::clerk/opts {:render-fn '(fn [x] [:h3 (pr-str @x)])}}
(defonce my-state
  (atom {:counter 0}))

;; This is showing the state that the JVM has.
@my-state

^{::clerk/viewer counter-viewer}
(do #'my-state)


#_(-> (atom {}) meta)


