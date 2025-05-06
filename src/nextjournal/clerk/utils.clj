(ns nextjournal.clerk.utils)

(def bb? (System/getProperty "babashka.version"))

(defmacro if-bb [then else]
  (if bb? then else))

(defmacro when-bb [& body]
  (when bb?
    `(do ~@body)))
