(ns nextjournal.devcards-test
  (:require [re-frame.context :as rf]
            [nextjournal.devcards :as dc]
            [nextjournal.devcards.main]))

(rf/reg-sub :db (fn [db] db))

(dc/defcard inspect-mini
  "notice how after triggering a live reload event the `pr-str` shows an empty map.

   The db inspector still shows `{:hello :world}` though."
  [:span
   (js/console.log :inspect-mini :frame-id (:frame-id (rf/current-frame)))
   (pr-str @(rf/subscribe [:db]))]
  {:hello :world})
