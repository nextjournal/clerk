(ns nextjournal.clerk.ssr
  "Server-side-rendering using `reagent.dom.server` on GraalJS.

  Currently wip, can load the js bundle but needs more conditional for
  `js/document` or exclude libraries."
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
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

(defn replace-self [script-contents]
  ;; shadow's esm target currently needs this replacement, see
  ;; https://clojurians.slack.com/archives/C6N245JGG/p1666353696590419
  (str/replace script-contents (re-pattern "self") "globalThis"))

(def viewer-js-source
  ;; run `bb build:js` on shell to generate
  (.build (Source/newBuilder "js" (replace-self (slurp "build/viewer.js" #_(@config/!asset-map "/js/viewer.js"))) "viewer.mjs")))

(def !eval-viewer-source
  (delay (.eval context viewer-js-source)))

(defn render [edn-string]
  (force !eval-viewer-source)
  (execute-fn context "nextjournal.clerk.static_app.ssr" edn-string))

(comment
  (time
   (render (slurp "build/static_app_state.edn"))))
