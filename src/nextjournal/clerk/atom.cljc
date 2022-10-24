(ns nextjournal.clerk.atom
  "Clerks two-way bindings."
  {:nextjournal.clerk/visibility {:code :hide}}
  (:refer-clojure :exclude [atom])
  (:require [clojure.core :as core]
            [nextjournal.clerk :as clerk]))

(defn atom
  [initial-state]
  (core/atom initial-state))

(def atom-viewer
  {:transform-fn (comp clerk/mark-presented
                       (clerk/update-val (fn [{:nextjournal.clerk/keys [var-from-def]}]
                                           (prn :var var-from-def)
                                           {:var-name (symbol var-from-def) :state @@var-from-def})))
   :render-fn '(fn [{:keys [var-name state]}]
                 (let [_ (js/console.log (pr-str "resolve" (resolve var-name)))
                       var (or (resolve var-name)
                               (do (js/console.log (pr-str (list 'defatom var-name (list 'atom state))))
                                   (intern (create-ns (symbol (namespace var-name)))
                                           (symbol (name var-name))
                                           (with-meta (reagent/atom state)
                                             {:var-name var-name}))))]
                   [nextjournal.clerk.render/inspect @@var]))})

^{::clerk/viewer atom-viewer}
(def my-state
  (atom {:counter 0}))

(def atom-inspector
  {:transform-fn (clerk/update-val symbol)
   :render-fn '(fn [x] [nextjournal.clerk.render/inspect x])})


^{::clerk/viewer atom-inspector}
(do #'my-state)


(def counter-viewer
  {:transform-fn (comp (clerk/update-val symbol)
                       clerk/mark-presented)
   :render-fn '(fn [var-name]
                 (js/console.log (pr-str var-name))
                 (let [atom @(resolve var-name)]
                   (js/console.log (pr-str atom))
                   [:div
                    [:h2 "Counter Example"]
                    [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(nextjournal.clerk.render/swap-fn! atom update :counter inc)} "+"]
                    [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(nextjournal.clerk.render/swap-fn! atom update :counter dec)} "-"]
                    [:button.px-2.py-1.bg-blue-200.mr-1 {:on-click #(nextjournal.clerk.render/swap-fn! atom (fn [_] {:counter 0}))} "reset"]
                    [nextjournal.clerk.render/inspect @atom]]))})

^{::clerk/viewer counter-viewer}
(do #'my-state)

^{::clerk/no-cache true
  ::clerk/viewer {:render-fn '(fn [x]
                                (apply swap! nextjournal.clerk.atom/my-state update :counter (eval x))
                                [nextjournal.clerk.render/inspect x])
                  :transform-fn clerk/mark-presented}}
'[+ 2]

^{::clerk/viewer {:transform-fn (clerk/update-val symbol)
                  :render-fn '(fn [x] [nextjournal.clerk.render/inspect x])}}
(do #'my-state)

#_(-> (atom {}) meta)

()
