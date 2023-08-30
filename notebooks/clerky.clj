;; # 📞 Clerk Call API
(ns clerky
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [nextjournal.clerk :as clerk]))

;; - consider taking care of swap! ourselves / leave pure API functions user facing
;; - consider wrapping views so to expose just `state` or `db` / no deref
;; - consider skipping API handler registration on recomputes

^{::clerk/sync true}
(defonce !state (atom 0))

(defn inc-counter
  {:clerk/api {:state !state}}
  [!state] (swap! !state inc))

(defn dec-counter
  {:clerk/api {:state !state}}
  [!state] (swap! !state dec))

(def render-view
  '(fn [!state]
     [:div.flex.justify-center
      [:button.h-10.w-10.text-xl.rounded-full.text-sky-800.bg-sky-200 {:on-click #(nextjournal.clerk.render/api-call 'inc-counter)} "+"]
      [:h1.mx-5 "Current Counter: " @!state]
      [:button.h-10.w-10.text-xl.rounded-full.text-sky-800.bg-sky-200 {:on-click #(nextjournal.clerk.render/api-call 'dec-counter)} "-"]]))

{:nextjournal.clerk/visibility {:result :show}}

(-> (clerk/eval-cljs '!state)
    (assoc-in [:nextjournal/viewer :render-fn] render-view))

#_(clerk/clear-cache!)
