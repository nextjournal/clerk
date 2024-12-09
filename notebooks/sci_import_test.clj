(ns sci-import-test
  (:require [nextjournal.clerk :as clerk]
            [clojure.core :as b]))

(clerk/with-viewer
  {:render-fn
   '(fn [_]
      (nextjournal.clerk.render/render-promise
       (->
        (js/import "https:/esm.sh/date-fns@3.0.0/index.mjs")
        (.then
         (fn [lib]
           (nextjournal.clerk/html
            [:pre (lib.formatRelative (lib.subDays (js/Date.) 2) (js/Date.))]))))))}
  nil)
