(ns my-random-namespace)

(defn macro-helper* [x] x)

(defmacro attempt1
  [& body]
  `(macro-helper* (try
                    (do ~@body)
                    (catch Exception e# e#))))

(def a1
  (do
    (println "a1")
    (attempt1 (rand-int 9999))))

#_(do (remove-ns 'my-random-namespace)
      (nextjournal.clerk/clear-cache!)
      (create-ns 'my-random-namespace))





