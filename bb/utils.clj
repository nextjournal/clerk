(ns utils
  (:require [babashka.process :refer [sh]]
            [clojure.string :as str]))

(defn latest-sha []
  (str/trim (:out (sh "git rev-parse HEAD"))))
