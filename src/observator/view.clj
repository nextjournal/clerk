(ns observator.view
  (:require [glow.core :as glow]
            [hiccup.page :as hiccup]))

(defn doc->viewer [doc]
  (into ^{:nextjournal/viewer :flex-col} []
        (mapcat (fn [{:keys [type text result]}]
                  (case type
                    :markdown [{:nextjournal/viewer type :nextjournal/value text}]
                    :code (cond-> [{:nextjournal/viewer :html :nextjournal/value (str "<pre class='code'>" (glow/highlight-html text) "</pre>")}]
                            result (conj result)))))
        doc))

#_(doc->viewer (observator.core/eval-file "src/observator/demo.clj"))

(defn ->edn [x]
  (binding [*print-meta* true
            *print-namespace-maps* false]
    (pr-str x)))

#_(->edn (let [file "src/observator/demo.clj"]
           (doc->viewer (hashing/hash file) (hashing/parse-file {:markdown? true} file))))

(def inline-style
  "body {
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
span.variable { color: #268bd2; }")

(defn ->html [viewer]
  (hiccup/html5
   [:head
    [:meta {:charset "UTF-8"}]
    (hiccup/include-css "https://cdn.jsdelivr.net/gh/tonsky/FiraCode@5.2/distr/fira_code.css")
    (hiccup/include-css "https://cdn.nextjournal.com/data/QmRqugy58UfVG5j9Lo2ccKpkV6tJ2pDDfrZXViRApUKG4v?filename=viewer-a098a51e8ec9999fae7673b325889dbccafad583.css&content-type=text/css")
    (hiccup/include-js "https://cdn.nextjournal.com/data/QmPxet4ijyt6s3pRrkqKn1JfV9DEvMTVhQ6p5qz1DhGjEF?filename=viewer.js&content-type=application/x-javascript")
    [:style inline-style]]
   [:body
    [:script "const url = 'ws://localhost:7777/_ws?' + window.location.pathname.replace('/notebook/', '');
const ws = new WebSocket(url);
ws.onmessage = msg => nextjournal.viewer.inspect_into(document.body, nextjournal.viewer.read_string(msg.data));"]
    [:script "nextjournal.viewer.inspect_into(document.body, nextjournal.viewer.read_string(" (-> viewer ->edn pr-str) "))"]]))
;; document.body.innerHTML = String(nextjournal.viewer);


(defn doc->html
  [doc]
  (->html (doc->viewer doc)))

#_(doc->html (observator.core/eval-file "src/observator/demo.clj"))

#_(let [doc (observator.core/eval-file "src/observator/demo.clj")
        out "test.html"]
    (spit out (->html doc))
    (clojure.java.browse/browse-url out))
