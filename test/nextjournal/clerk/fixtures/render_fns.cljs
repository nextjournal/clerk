(ns nextjournal.clerk.fixtures.render-fns)

(defn id [x]
  (prn :identity)
  [:pre (nextjournal.clerk/inspect x)])

