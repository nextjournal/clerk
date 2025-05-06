(ns viewers.cljs
  (:require [nextjournal.clerk :as clerk]))

(defn cljs-render
  "Takes a symbol pointing at a cljs render function and constructs a
  viewer that looks up the `:render-fn` in the global `js`
  environment, without going through sci if `cljs.compiler/munge` can
  be resolved."
  [sym & args]
  (apply clerk/with-viewer
         {:transform-fn clerk/mark-presented
          :render-fn (if-let [cljs-munge (resolve 'cljs.compiler/munge)]
                       (symbol "js" (name (cljs-munge sym)))
                       sym)}
         args))


(cljs-render 'nextjournal.clerk.render/triangle true)
