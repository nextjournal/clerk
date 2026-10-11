(ns nextjournal.clerk.datastar-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [nextjournal.clerk.datastar :as datastar]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.html :as html]
            [nextjournal.clerk.view :as view])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream EOFException)
           (java.util.zip GZIPInputStream)))

(defn present-string [s]
  (view/doc->viewer (eval/eval-string s)))

(deftest render-notebook
  (let [presented (present-string "(ns datastar-test.render (:require [nextjournal.clerk :as clerk]))
;; # Hello
{:a [1 2 3] :b \"multi\\nline\" :c (range 30)}
(clerk/table [[1 2] [3 4]])
(clerk/html [:div.p-2 (clerk/md \"**md**\")])
(ex-info \"boom\" {:a 1})")
        out (html/->html-str (html/inspect {} presented))]
    (testing "all default viewers used render on the server"
      (is (not (str/includes? out "requires the browser renderer"))))
    (is (str/includes? out "<h1 id=\"hello\">Hello</h1>"))
    (is (str/includes? out "<strong>md</strong>"))
    (is (str/includes? out "boom"))
    (testing "elisions are not fetchable without an `:on-fetch` callback"
      (is (str/includes? out "10 more elided")))))

(deftest tab-state
  (let [presented (present-string "(ns datastar-test.tabs) (def x {:a (range 30)})")
        [id {:keys [hash]}] (first (datastar/result-index presented))
        tab {:results {id {:hash hash :flipped #{[1] [1 0 1 5]}}
                       "gone-result" {:hash "x"}}}]
    (testing "drops state of results that are gone and keeps unchanged ones"
      (is (= {id {:hash hash :flipped #{[1] [1 0 1 5]}}}
             (:results (datastar/prune-tab tab (datastar/result-index presented))))))
    (testing "drops stale paths when a result changed"
      (let [changed (present-string "(ns datastar-test.tabs) (def x {:a 1})")
            [id' {hash' :hash}] (first (datastar/result-index changed))]
        (is (= id id'))
        (is (= {id {:hash hash' :flipped #{[1]}}}
               (:results (datastar/prune-tab tab (datastar/result-index changed)))))))
    (testing "flipped paths toggle the computed expansion"
      (let [{:keys [expanded-at]} ((datastar/result-state-fn tab) id (get-in (datastar/result-index presented) [id :presented]) false)]
        (is (true? (get expanded-at [1])))))))

(deftest sse-event
  (is (= "event: datastar-patch-elements\ndata: elements <main id=\"clerk\">\ndata: elements a</main>\n\n"
         (datastar/sse-event "<main id=\"clerk\">\na</main>")))
  (testing "a carriage return can't break the event framing"
    (is (= 2 (count (re-seq #"data: elements" (datastar/sse-event "a\rb"))))))
  (is (str/starts-with? (datastar/sse-event {:selector "body" :mode "append"} "<script></script>")
                        "event: datastar-patch-elements\ndata: selector body\ndata: mode append\n")))

(defn gunzip
  "Decompresses a gzip stream that hasn't been closed yet."
  [^bytes bs]
  (let [out (ByteArrayOutputStream.)
        in (GZIPInputStream. (ByteArrayInputStream. bs))]
    (try (loop []
           (let [b (.read in)]
             (when-not (neg? b)
               (.write out b)
               (recur))))
         (catch EOFException _))
    (String. (.toByteArray out) "UTF-8")))

(deftest compression
  (is (= "gzip" (:content-encoding (datastar/negotiate-encoder "gzip, deflate, br"))))
  (is (nil? (:content-encoding (datastar/negotiate-encoder nil))))
  (testing "the gzip stream is flushed after every event"
    (let [{:keys [encode close]} (datastar/gzip-encoder)
          chunk-1 (encode (.getBytes "event one\n\n"))
          chunk-2 (encode (.getBytes "event two\n\n"))]
      (close)
      (is (pos? (alength ^bytes chunk-1)))
      (is (str/starts-with? (gunzip (byte-array (concat chunk-1 chunk-2))) "event one\n\nevent two\n\n")))))

(deftest tab-ids
  (is (datastar/valid-tab-id? (datastar/new-tab-id)))
  (is (not (datastar/valid-tab-id? "x');alert(1)//")))
  (is (not (datastar/valid-tab-id? nil))))

(deftest action-requests
  (let [req (fn [headers] {:request-method :post :uri "/_clerk/action/eval" :query-string "form=nil" :headers headers})]
    (testing "rejects requests without the datastar header (e.g. a cross-origin form post)"
      (is (= 403 (:status (datastar/action-handler (req {"host" "localhost:7777"}))))))
    (testing "rejects requests from another origin"
      (is (= 403 (:status (datastar/action-handler (req {"host" "localhost:7777" "datastar-request" "true" "origin" "https://evil.example"}))))))
    (is (datastar/same-origin-datastar-request? {:headers {"host" "localhost:7777" "datastar-request" "true" "origin" "http://localhost:7777"}}))
    (testing "tab ids are required for tab specific actions"
      (is (= 400 (:status (datastar/action-handler {:request-method :post :uri "/_clerk/action/toggle-expand" :query-string ""
                                                    :headers {"datastar-request" "true"}})))))))

(def !synced (atom 1))

(deftest sync-action
  (testing "only synced vars of the current notebook can be updated"
    (is (thrown-with-msg? Exception #"is not a synced var"
                          (datastar/handle-action "sync" "tab" {:var (pr-str `!synced) :coerce ":number" :value "2"}))))
  (testing "updates arriving out of order are dropped"
    (let [sym (gensym "var")]
      (is (datastar/newer-sync? "tab" sym 2))
      (is (not (datastar/newer-sync? "tab" sym 1)))
      (is (datastar/newer-sync? "tab" sym 3))
      (is (datastar/newer-sync? "other-tab" sym 1))))
  (is (= 2 (datastar/parse-number "2")))
  (is (= 0.5 (datastar/parse-number "0.5")))
  (is (thrown? Exception (datastar/parse-number "(launch-missiles)"))))

(deftest scripts
  (testing "strings are safe to embed in a script tag"
    (is (= "\"<\\/script>\\\"\"" (datastar/js-str "</script>\""))))
  (is (str/includes? (datastar/push-url-script "'nextjournal.clerk.home" "Home") "\"'nextjournal.clerk.home\"")))

(deftest controls
  (let [presented (present-string "(ns datastar-test.controls (:require [nextjournal.clerk :as clerk] [nextjournal.clerk.experimental :as cx]))
^{::clerk/sync true ::clerk/viewer cx/slider} (defonce !n (atom 3))
^{::clerk/sync true ::clerk/viewer cx/text-input} (defonce !s (atom \"hi\"))
(clerk/with-viewer (assoc nextjournal.clerk.viewer/render-eval-viewer :render-fn '(fn [x] [:div @x])) `!n)")]
    (testing "without callbacks controls render disabled with their value"
      (let [out (html/->html-str (html/inspect {} presented))]
        (is (re-find #"<input[^>]*type=\"range\"[^>]*value=\"3\"" out))
        (is (re-find #"<input[^>]*disabled[^>]*value=\"hi\"|<input[^>]*value=\"hi\"[^>]*disabled" out))
        (is (str/includes? out "A custom `:render-fn` for `nextjournal.clerk.viewer/render-eval-viewer`"))))
    (testing "with datastar the value is applied via data-init unless focused"
      (let [out (html/->html-str (html/inspect (datastar/render-ctx "tab" nil) presented))]
        (is (str/includes? out "data-init=\"if (document.activeElement !== el) el.value = &quot;3&quot;\""))
        (is (str/includes? out "/_clerk/action/sync?var=datastar-test.controls%2F%21n"))))))

(deftest syntax-highlighting
  (let [code "(defn ^:private foo\n  \"doc\n  string\" [x]\n  ;; comment\n  (re-find #\"a\\\"b\" (str x \\a :k ::k/x nil true 1.5)))"
        tokens (html/tokenize-clojure code)]
    (testing "tokens contain the exact source"
      (is (= code (apply str (map second tokens)))))
    (is (= [["cmt-keyword" "defn"] ["cmt-meta" "^"] ["cmt-atom" ":private"] ["cmt-variableName" "foo"]
            ["cmt-string" "\"doc\n  string\""] ["cmt-comment" ";; comment"] ["cmt-regexp" "#\"a\\\"b\""]
            ["cmt-string" "\\a"] ["cmt-atom" ":k"] ["cmt-atom" "::k/x"] ["cmt-atom" "nil"] ["cmt-bool" "true"]]
           (filter first tokens)))
    (testing "multi-line tokens are split across lines"
      (is (= 5 (count (html/tokens->lines tokens))))))
  (testing "unbalanced or unknown input is passed through"
    (is (= "(foo \"bar" (apply str (map second (html/tokenize-clojure "(foo \"bar")))))))

(deftest json
  (is (= "{\"a\":[1,2.5,0.5,null,null,true],\"b\\n\\\"\":[\"x\"],\"opts\":\"sym\"}"
         (html/->json (array-map :a [1 2.5 1/2 ##NaN nil true] "b\n\"" #{:x} :embed/opts 'sym))))
  (is (= "\"\\u0001\"" (html/->json (str (char 1))))))

(deftest client-components
  (let [render (fn [n] (html/->html-str (html/inspect {} (present-string (str "(ns datastar-test.components (:require [nextjournal.clerk :as clerk]))
(clerk/vl {:data {:values (range " n ")} :mark \"bar\"})")))))
        id (fn [out] (second (re-find #"<clerk-vega-lite[^>]* id=\"([^\"]+)\"" out)))]
    (is (str/includes? (render 3) "data-ignore-morph"))
    (testing "the id changes with the content so morphing replaces the component"
      (is (= (id (render 3)) (id (render 3))))
      (is (not= (id (render 3)) (id (render 4)))))))

(deftest islands
  (let [presented (present-string "(ns datastar-test.islands (:require [nextjournal.clerk :as clerk]))
(clerk/with-viewer '(fn [x] [:div.custom x]) 42)
(clerk/html [:div ['nextjournal.clerk.render/inspect 1]])")
        out (html/->html-str (html/inspect (datastar/render-ctx "tab" nil) presented))]
    (testing "values needing the browser render as islands carrying their presented edn"
      (is (= 2 (count (re-seq #"<clerk-island " out))))
      (is (str/includes? out "(fn [x] [:div.custom x])")))
    (testing "the island env carries required cljs and synced atoms"
      (is (= :clerk-island-env (first (datastar/island-env presented)))))))

(def !island-synced (atom {:n 1}))

(deftest island-messages
  (let [post (fn [msg & {:keys [headers]}]
               (datastar/island-handler {:request-method :post
                                         :headers (merge {"datastar-request" "true"} headers)
                                         :body (java.io.ByteArrayInputStream. (.getBytes (pr-str msg)))}))]
    (testing "clerk-eval replies with the result"
      (let [{:keys [status body]} (post {:type :eval :eval-id 'e1 :form '(+ 1 2)})]
        (is (= 200 status))
        (is (= {:type :eval-reply :eval-id 'e1 :reply 3} (read-string body)))))
    (testing "only same-origin datastar requests are accepted"
      (is (= 403 (:status (post {:type :eval :form '(+ 1 2)} :headers {"datastar-request" nil})))))
    (testing "only synced vars of the current notebook can be patched"
      (is (= 500 (:status (post {:type :sync! :var-name `!island-synced :patch [[[:n] :r 2]]}))))
      (is (= {:n 1} @!island-synced)))))

(deftest static-pages
  (let [presented (present-string "(ns datastar-test.static (:require [nextjournal.clerk :as clerk]))
;; # Static
(clerk/with-viewer '(fn [x] [:div x]) 1)
(range 30)")
        opts {:resource->url {"/js/viewer.js" "https://cdn.example/viewer.js"}}
        page (datastar/static-page-html opts {:presented presented})]
    (testing "static pages don't use datastar or the server"
      (is (not (str/includes? page "datastar.js")))
      (is (not (str/includes? page "/_clerk/")))
      (is (not (str/includes? page "<meta name=\"clerk-island-endpoint\""))))
    (is (str/includes? page "<title>Static</title>"))
    (is (str/includes? page "customElements.define('clerk-island'"))
    (is (str/includes? page "content=\"https://cdn.example/viewer.js\""))
    (is (str/includes? page "10 more elided"))
    (testing "single file builds render all docs as sections"
      (let [page (datastar/static-page-html opts {:path->doc {"" presented "other" presented}})]
        (is (= 2 (count (re-seq #"<section data-clerk-path" page))))
        (is (str/includes? page "data-clerk-path=\"other\" data-clerk-title=\"Static\" hidden"))))))
