(ns nextjournal.clerk.ssr
  "Server-side-rendering using `reagent.dom.server` on GraalJS.

  Status: working in node but not yet in GraalJS.

  To run it in node:

  1. run the do block in the comment form at the end of this file
  2. change the runtime to `:custom` in shadow-cljs.edn
  3. run the following on your terminal

      $ cd ui_tests; yarn nbb -m ssr --file ../build/static_app_state_hello.edn

  Let's priorize integrating SSR via node first. Once this works and
  we want to get it running in GraalJS, we should first upgrade graal
  to `org.graalvm.js/js {:mvn/version \"22.3.0\"}`

  With this, we're running into https://github.com/facebook/react/issues/24851."
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
  (.build (Source/newBuilder "js" (slurp (viewer-js-path)) "viewer.mjs")))

(def !eval-viewer-source
  (delay (.eval context viewer-js-source)))

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
          (as-> doc+viewer (builder/build-static-app-opts (builder/process-build-opts {:index file}) [doc+viewer]))))

    (spit "build/static_app_state_hello.edn" (pr-str (file->static-app-opts "notebooks/hello.clj")))
    (spit "build/static_app_state_rule_30.edn" (pr-str (file->static-app-opts "notebooks/rule_30.clj")))

    (time (render (slurp "build/static_app_state_hello.edn")))))


