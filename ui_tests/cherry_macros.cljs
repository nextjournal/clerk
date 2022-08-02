(ns cherry-macros
  (:refer-clojure :exclude [->>]))

(defmacro assert! [v msg]
  `(when-not ~v
     (set! js/process.exitCode 1)
     (throw (js/Error. ~msg))))
