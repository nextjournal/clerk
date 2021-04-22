(ns observator.webview
  "Sets up a JavaFX WebView.

  Mostly taken from https://gist.github.com/jackrusher/626e0d97282c089cf56e"
  (:require [glow.core :as glow]
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

(defn load-url! [url]
  (run-later (.load @engine url)))

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

(defn doc->viewer [var->hash doc]
  (into ^{:nextjournal/viewer :flex-col} []
        (mapcat (fn [{:keys [type text]}]
                  (case type
                    :markdown [{:nextjournal/viewer type :nextjournal/value text}]
                    ;; TODO: bring back syntax highlighting via glow
                    :code [{:nextjournal/viewer :html :nextjournal/value (str "<pre class='code'>" (glow/highlight-html text) "</pre>")}
                           (obs/read+eval-cached var->hash text)])))
        doc))

(defn ->edn [x]
  (binding [*print-meta* true
            *print-namespace-maps* false]
    (pr-str x)))

#_(->edn (let [file "src/observator/demo.clj"]
           (doc->viewer (hashing/hash file) (hashing/parse-file {:markdown? true} file))))

(defn ->html [viewer]
  (str "<!DOCTYPE html>
<html><head>
<meta charset=\"UTF-8\">
<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/gh/tonsky/FiraCode@5.2/distr/fira_code.css\">
<link rel=\"stylesheet\" href=\"https://cdn.nextjournal.com/data/QmRqugy58UfVG5j9Lo2ccKpkV6tJ2pDDfrZXViRApUKG4v?filename=viewer-a098a51e8ec9999fae7673b325889dbccafad583.css&content-type=text/css\"/>
<script src=\"https://cdn.nextjournal.com/data/QmPxet4ijyt6s3pRrkqKn1JfV9DEvMTVhQ6p5qz1DhGjEF?filename=viewer.js&content-type=application/x-javascript\"></script>
<style>
body {
  font: 20px Georgia;
  padding: 0.5em;
}
pre {
  font: 16px 'Fira Code';
}
.code {
  background-color: rgb(245, 245, 245);
  padding: 0.5em;
}
.result {
  padding-left: 0.5em;
}

span.background { color: #f6f6f6; } /* not used? */
span.s-exp { color: #777777; }
span.reader-char { color: #777777; }
span.regex { color: #777777; }
span.comment { color: #777777; }
span.number { color: #777777; }

span.definition { color: #DE5772; }
span.special-form { color: #DE5772; }
span.macro { color: #DE5772; }

span.keyword { color: #BF60B4; }
span.nil { color: #BF60B4; }
span.boolean { color: #BF60B4; }

span.core-fn { color: #9085DA; }
span.symbol { color: #9085DA; }

span.repeat { color: #2CAB76; }
span.exception { color: #2CAB76; }
span.conditional { color: #2CAB76; }
span.character { color: #2CAB76; }

span.string { color: #C7877B; }
span.variable { color: #268bd2; }"
       "</style></head><body>"
       "<script>
nextjournal.viewer.inspect_into(document.body, nextjournal.viewer.read_string(" (-> viewer ->edn pr-str) "))
</script>"
       "</body></html>\n"))

(defn file->html
  [file]
  (let [var->hash (hashing/hash file)]
    (->> file
         (hashing/parse-file {:markdown? true})
         (doc->viewer var->hash)
         ->html)))

#_(let [out "demo.html"]
    (->> "src/observator/demo.clj" file->html (spit out))
    (clojure.java.browse/browse-url out)
    (load-url! (str "file:/Users/mk/dev/observator/" out)))

#_(set-html! (file->html "src/observator/demo.clj"))
#_(set-html! "<h1>hello world</h1>")
