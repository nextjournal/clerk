(ns nextjournal.clerk.ssr
  "Server-side-rendering using `reagent.dom.server` on GraalJS."
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

(defn viewer-js-source []
  ;; tiny utf8 only `TextEncoder` polyfill
  (str (slurp "https://gist.githubusercontent.com/Yaffle/5458286/raw/1aa5caa5cdd9938fe0fe202357db6c6b33af24f4/TextEncoderTextDecoder.js")
       "\n"
       (slurp (@config/!asset-map "/js/viewer.js"))))

(def !eval-viewer-source
  (delay (.eval context (.build (Source/newBuilder "js" (viewer-js-source) "viewer.mjs")))))

(defn render [edn-string]
  (force !eval-viewer-source)
  (execute-fn context "nextjournal.clerk.static_app.ssr" edn-string))


(comment
  (do
    (require '[nextjournal.clerk :as clerk]
             '[nextjournal.clerk.eval :as eval]
             '[nextjournal.clerk.builder :as builder]
             '[nextjournal.clerk.view :as view])

    (defn file->static-app-opts [file]
      (-> (eval/eval-file file)
          (as-> doc (assoc doc :viewer (view/doc->viewer {} doc)))
          (as-> doc+viewer
            (builder/build-static-app-opts (builder/process-build-opts {:index file}) [doc+viewer])
            (assoc doc+viewer :current-path file))))

    (spit "build/static_app_state_hello.edn" (pr-str (file->static-app-opts "notebooks/hello.clj")))
    (spit "build/static_app_state_rule_30.edn" (pr-str (file->static-app-opts "notebooks/rule_30.clj")))

    (time (render (slurp "build/static_app_state_rule_30.edn")))))


