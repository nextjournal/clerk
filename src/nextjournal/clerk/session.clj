(ns nextjournal.clerk.session)

(defn in-session-ns [{:keys [ns session-ns]} var]
  (if (and var session-ns)
    (symbol (str session-ns)
            (name var))
    var))

(def session-ns-prefix
  "nextjournal.clerk.synthetic-session.")

(defn session-ns-name [{:keys [ns session]}]
  (symbol (str session-ns-prefix (ns-name ns) ".session=" session)))

#_(session-ns-name {:ns (create-ns 'scratch) :session 'foo})

(defn rewrite-ns-form [doc session-ns]
  (update-in doc [:blocks 0 :form] (fn [ns-form]
                                     (concat [(first ns-form)
                                              (ns-name session-ns)]
                                             (drop 2 ns-form)))))

