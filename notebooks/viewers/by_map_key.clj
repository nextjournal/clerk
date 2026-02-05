;; # 🔪 By Map Key
^{:nextjournal.clerk/visibility {:code :hide}}
(ns notebooks.viewers.by-map-key
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))


;; When modeling a domain with namespaced keys in Clojure, it would
;; often be nice to select a given viewer for a value in the map,
;; based on the key.

;; Let's start with an example: `vin-viewer` is a viewer for a [Vehicle identification number](https://en.wikipedia.org/wiki/Vehicle_identification_number).
(def vin-viewer
  {:pred string?
   :transform-fn (clerk/update-val (fn [vin] (clerk/html (let [[head rest] (split-at 10 vin)]
                                                           [:span.font-mono.text-xs
                                                            head
                                                            [:b rest]]))))})

;; We can manually select it using `clerk/with-viewer`.

(clerk/with-viewer vin-viewer
  "WBAJD51080BL18969")

(def key->viewer
  {:vehicle/vin vin-viewer})


;; Or create a viewer to select it automatically for values associated with the `:vehicle/vin` key.

(clerk/with-viewers (clerk/add-viewers [{:pred {:wrapped (fn [{:as wrapped-value :keys [path]}]
                                                           (and (map-entry? (clerk/->value wrapped-value))
                                                                (contains? key->viewer (key (clerk/->value wrapped-value)))
                                                                ;; make sure we only add it once
                                                                ;; TOOD: use better check
                                                                (not (some (comp `#{key->viewer} :name) (:nextjournal/viewers wrapped-value)))))}
                                         :transform-fn (fn [{:as wrapped-value :keys [path]}]
                                                         (let [val-viewer (get key->viewer (key (clerk/->value wrapped-value)))]
                                                           (update wrapped-value
                                                                   :nextjournal/viewers
                                                                   clerk/add-viewers
                                                                   [{:name `key->viewer
                                                                     :pred {:wrapped (fn [wrapped-value']
                                                                                       (= (:path wrapped-value')
                                                                                          (conj path 1)))}
                                                                     :transform-fn (partial clerk/with-viewer val-viewer)}])))}])
  {:vehicle/vin "WBAJD51080BL18969"})




