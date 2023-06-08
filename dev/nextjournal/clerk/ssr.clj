(ns nextjournal.clerk.ssr
  "Server-side-rendering using `reagent.dom.server` on GraalJS.

  Status: working in GraalJS `org.graalvm.js/js {:mvn/version \"22.3.0\"}`

  To try this ad the dep above to e.g. the `:sci` alias."
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

(defn viewer-js-path []
  (@config/!asset-map "/js/viewer.js")
  ;; uncomment the following to test against a local js bundle
  #_"build/viewer.js")

(def viewer-js-source
  ;; run `bb build:js` on shell to generate
  (.build (Source/newBuilder "js" (str (slurp "https://gist.githubusercontent.com/Yaffle/5458286/raw/1aa5caa5cdd9938fe0fe202357db6c6b33af24f4/TextEncoderTextDecoder.js") ;; tiny utf8 only TextEncoder polyfill
                                       "\n"
                                       (slurp (viewer-js-path))) "viewer.mjs")))


(def !eval-viewer-source
  (delay (.eval context viewer-js-source)))

(defn render [edn-string]
  (force !eval-viewer-source)
  (execute-fn context "nextjournal.clerk.sci_env.ssr" edn-string))


(comment
  (do
    (require '[nextjournal.clerk :as clerk]
             '[nextjournal.clerk.eval :as eval]
             '[nextjournal.clerk.builder :as builder]
             '[nextjournal.clerk.view :as view])

    (defn file->static-app-opts [file]
      (-> (eval/eval-file file)
          (as-> doc (assoc doc :viewer (view/doc->viewer {} doc)))
          (as-> doc+viewer (builder/build-static-app-opts (builder/process-build-opts {:index file}) [doc+viewer]))))

    (spit "build/static_app_state_hello.edn" (pr-str (file->static-app-opts "notebooks/hello.clj")))
    (spit "build/static_app_state_rule_30.edn" (pr-str (file->static-app-opts "notebooks/rule_30.clj")))

    (time (render (slurp "build/static_app_state_hello.edn")))))


