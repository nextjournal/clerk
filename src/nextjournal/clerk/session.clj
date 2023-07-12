(ns nextjournal.clerk.session
  (:require [clojure.string :as str]
            [nextjournal.clerk.analyzer :as analyzer]))

(defn in-session-ns [{:keys [ns session-ns]} var]
  (if (and var session-ns)
    (symbol (str session-ns)
            (name var))
    var))

(def session-ns-prefix
  "nextjournal.clerk.synthetic-session.")

(def session-ns-pattern
  (re-pattern (str "^" session-ns-prefix "\\w+\\.")))

(defn session-ns-name [{:keys [ns session]}]
  (symbol (str session-ns-prefix (analyzer/valuehash session) "." (ns-name ns))))

(defn ->orignal-ns [sym]
  (if (qualified-symbol? sym)
    (symbol (str/replace (namespace sym) session-ns-pattern "")
            (name sym))
    sym))

(defn rewrite-ns-form [doc session-ns]
  (update-in doc [:blocks 0 :form] (fn [ns-form]
                                     (concat [(first ns-form)
                                              (ns-name session-ns)]
                                             (drop 2 ns-form)))))

