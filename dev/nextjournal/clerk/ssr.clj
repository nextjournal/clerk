(ns nextjournal.clerk.ssr
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [nextjournal.clerk.config :as config])
  (:import (org.graalvm.polyglot Context Source)))

(def context-builder
  (doto (Context/newBuilder (into-array ["js"]))
    (.option "js.timer-resolution" "1")
    (.option "js.java-package-globals" "false")
    (.out System/out)
    (.err System/err)
    (.allowAllAccess true)
    (.allowNativeAccess true)))

(def context (.build context-builder))

(defn execute-fn [context fn & args]
  (let [fn-ref (.eval context "js" fn)
        args (into-array Object args)]
    (assert (.canExecute fn-ref) (str "cannot execute " fn))
    (.execute fn-ref args)))

(def viewer-js-source
  (.build (Source/newBuilder "js" (slurp "build/viewer.js" #_(@config/!asset-map "/js/viewer.js")) "viewer.js")))

(def polyfill-js-source
  (.build (Source/newBuilder "js" "function setTimeout(t) { };" "polyfills.js")))

(comment
  (.eval context polyfill-js-source)
  (.eval context viewer-js-source)
  
  (execute-fn context "nextjournal.clerk.static_app.ssr" (slurp "build/static_app_state.edn")))
