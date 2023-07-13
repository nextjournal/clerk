(ns nextjournal.clerk.session
  (:require [clojure.string :as str]
            [multiformats.base.b58 :as b58]
            [multiformats.hash :as hash]))

(defn in-session-ns [{:keys [ns session-ns]} var]
  (if (and var session-ns)
    (symbol (str session-ns)
            (name var))
    var))

(defn in-main-ns [{:keys [ns session-ns]} var]
  (if (and var session-ns)
    (symbol (str ns)
            (name var))
    var))

(def session-ns-prefix
  "nextjournal.clerk.synthetic-session.")

(def session-ns-pattern
  (re-pattern (str "^" session-ns-prefix "\\w+\\.")))

(defn ^:private valuehash [session]
  (->> session pr-str hash/sha2-512 hash/encode b58/format-btc))

(defn session-ns-name [{:keys [ns session]}]
  (symbol (str session-ns-prefix (valuehash session) "." (ns-name ns))))

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


(defn deref-dep-in-session [{:as doc :keys [ns session-ns]} deref-dep]
  (when-not (and (= 2 (count deref-dep))
                 (qualified-symbol? (second deref-dep)))
    (throw (ex-info "deref-dep must be of form `(deref dep)`" {:deref-dep deref-dep})))
  (if (and session-ns (= (namespace (second deref-dep)) (str ns)))
    (list (first deref-dep) (symbol (str session-ns) (name (second deref-dep))))
    deref-dep))
