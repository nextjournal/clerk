(ns sci-import-test
  (:require [nextjournal.clerk :as clerk]
            [clojure.core :as b]))

1

(clerk/with-viewer
  {:render-fn
   '(fn [_]
      (nextjournal.clerk.render/render-promise
       (->
        (js/import "https:/esm.sh/date-fns@3.0.0/index.mjs")
        (.then
         (fn [lib]
           (js/console.log :>1 lib.formatRelative)
           (js/console.log :>2 lib.subDays)
           (nextjournal.clerk/html
            [:pre (lib.formatRelative (lib.subDays (js/Date.) 2) (js/Date.))]))))))}
  nil)
