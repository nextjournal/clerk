(ns observator.webview
  "Sets up a JavaFX WebView.

  Mostly taken from https://gist.github.com/jackrusher/626e0d97282c089cf56e"
  (:require [clojure.string :as str]
            [observator.lib :as obs.lib]
            [observator.core :as obs]
            [observator.hashing :as hashing])
  (:import (javafx.scene Scene)
           (javafx.scene.web WebView)))

(defn run-later*
  [f]
  (javafx.application.Platform/runLater f))

(defmacro run-later
  [& body]
  `(run-later* (fn [] ~@body)))

(defn run-now*
  [f]
  (let [result (promise)]
    (run-later
     (deliver result (try (f) (catch Throwable e e))))
    @result))

(defmacro run-now
  [& body]
  `(run-now* (fn [] ~@body)))

(defn native!
  "Setup native look & feel, from seesaw, see:
  https://github.com/clj-commons/seesaw/blob/4ca89d0dcdf0557d99d5fa84202b7cc6e2e92263/src/seesaw/core.clj#L70-L86"
  []
  (System/setProperty "apple.laf.useScreenMenuBar" "true")
  (javax.swing.UIManager/setLookAndFeel (javax.swing.UIManager/getSystemLookAndFeelClassName)))

(native!) ;; needs to run before the swing calls

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; a couple of helpers for the webview


(def web-view (atom nil))

(def engine (atom nil))

(defn set-html! [html]
  (run-later (.loadContent @engine html)))

(defonce web-view-panel (javafx.embed.swing.JFXPanel.))

(defn make-frame! [panel]
  (let [frm (javax.swing.JFrame. "Observator")
        cp (.getContentPane frm)]
    (.setLayout cp (java.awt.BorderLayout.))
    (.add cp panel (java.awt.BorderLayout/CENTER))
    (.pack frm)
    (.setVisible frm true)
    frm))

(defn setup-web-view! []
  (run-now
   (reset! web-view (doto (WebView.) (.setPrefHeight 900)))
   (reset! engine (.getEngine @web-view))
   (set-html! "<h1>Hello Engine</h1>")
   (.setScene web-view-panel
              (Scene.  @web-view 900 1200))
   (make-frame! web-view-panel)))

(defonce frame (setup-web-view!))


(defn file->html
  [file]
  (let [var->hash (hashing/hash file)]
    (->> file
         (hashing/parse-file {:markdown? true})
         (map (fn [{:keys [type text]}]
                (cond-> (case type
                          :code (str "<pre>" text "</pre>")
                          :markdown (obs/md->html text))
                  (= :code type)
                  (str "<pre>" (obs/format-eval-output (obs/read+eval-cached var->hash text)) "</pre>"))))
         (str/join))))

#_(set-html! (file->html "src/observator/demo.clj"))
