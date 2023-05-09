(ns inst
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.experimental :as cx]))



^{::clerk/visibility {:result :hide}}
(def viewers
  (clerk/update-viewers viewer/default-viewers
                        {#{viewer/fallback-viewer} #(-> %
                                                        (assoc :name `viewer/fallback-viewer)
                                                        (assoc :render-fn '(fn [x]
                                                                             [:div (str x)]))
                                                        (dissoc :transform-fn))}))
^{::clerk/visibility {:result :hide} ::clerk/no-cache true}
(clerk/reset-viewers! viewers)

(java.util.Date.)
