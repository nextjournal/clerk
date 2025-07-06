(ns nextjournal.beholder
  "Babashka replacement for nextjournal.beholder"
  (:require [babashka.fs :as fs]
            [babashka.pods :as pods]))

(def watcher-version "0.0.7")

(def load-pod (delay (pods/load-pod 'org.babashka/fswatcher watcher-version)))

(defprotocol IWatchers
  (-stop [_]))

(defrecord Watchers [ws]
  IWatchers
  (-stop [_]
    (let [unwatch (requiring-resolve 'pod.babashka.fswatcher/unwatch)]
      (run! unwatch ws))))

(defn normalize-response [resp]
  (-> resp
      (update :path fs/path)
      (update :type (fn [k]
                      (if (#{:chmod :write} k) :modify
                          k)))))

(defn watch [cb & paths]
  @load-pod
  (let [cb (comp cb normalize-response)
        watch (requiring-resolve 'pod.babashka.fswatcher/watch)]
    (->Watchers (mapv #(watch % cb) paths))))

(defn stop [w]
  (-stop w))

(comment
  (def w (watch prn "." "/tmp"))
  (spit "/tmp/dude.txt" "hello")
  (stop w)
  #_(babashka.deps/add-deps '{:deps {com.github.nextjournal/clerk {:local/root "."}}})
  )
