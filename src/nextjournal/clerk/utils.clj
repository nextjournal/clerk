(ns nextjournal.clerk.utils)

(def bb? (System/getProperty "babashka.version"))

(defmacro if-bb [then else]
  (if bb? then else))

(defmacro if-not-bb-and [conds then else]
  (if bb?
    else
    `(if ~conds
       ~then
       ~else)))

(defmacro when-bb [& body]
  (when bb?
    `(do ~@body)))

(defmacro when-not-bb [& body]
  (when (not bb?)
    `(do ~@body)))
