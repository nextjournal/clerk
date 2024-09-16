(ns nextjournal.clerk.fixtures.render-fns)

(prn ::identity)

(defn id [x]
  [:pre (nextjournal.clerk/inspect x)])

