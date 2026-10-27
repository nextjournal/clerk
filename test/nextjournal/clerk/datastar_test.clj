(ns nextjournal.clerk.datastar-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [nextjournal.clerk.datastar :as datastar]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.html :as html]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver])
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
  (is (str/includes? (datastar/push-url-script "'nextjournal.clerk.home" "Home") "\"'nextjournal.clerk.home\""))
  (testing "popstate only navigates when the path changed, not for in-page anchors like toc links"
    (let [page (datastar/page-html {} "tab" {})]
      (is (str/includes? page "data-on:popstate__window=\"if (decodeURIComponent(location.pathname) !== document.documentElement.dataset.clerkPath)"))
      (is (str/includes? page "document.documentElement.dataset.clerkPath = decodeURIComponent(location.pathname)")))))

(deftest controls
  (let [presented (present-string "(ns datastar-test.controls (:require [nextjournal.clerk :as clerk] [nextjournal.clerk.experimental :as cx]))
^{::clerk/sync true ::clerk/viewer cx/slider} (defonce !n (atom 3))
^{::clerk/sync true ::clerk/viewer cx/text-input} (defonce !s (atom \"hi\"))
(clerk/with-viewer (assoc nextjournal.clerk.viewer/render-eval-viewer :render-fn '(fn [x] [:div @x])) `!n)")]
    (testing "without callbacks controls render disabled with their value"
      (let [out (html/->html-str (html/inspect {:islands? true} presented))]
        (is (re-find #"<input[^>]*type=\"range\"[^>]*value=\"3\"" out))
        (is (re-find #"<input[^>]*disabled[^>]*value=\"hi\"|<input[^>]*value=\"hi\"[^>]*disabled" out))))
    (testing "without islands a value whose viewer needs the browser throws"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"A custom `:render-fn` for `nextjournal.clerk.viewer/render-eval-viewer` needs the browser"
                            (html/->html-str (html/inspect {} presented)))))
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
        (is (= 2 (count (re-seq #"<template data-clerk-path" page))))
        (is (str/includes? page "<template data-clerk-path=\"other\" data-clerk-title=\"Static\">"))
        (is (str/includes? page "<div id=\"clerk-page\"></div>"))))))

(deftest toc
  (let [presented (present-string "(ns datastar-test.toc {:nextjournal.clerk/toc :collapsed})
;; # Title
;; ## Section")
        out (html/->html-str (html/inspect {} presented))]
    (is (str/includes? out "<clerk-toc data-default=\"closed\">"))
    (is (str/includes? out "href=\"#section\""))
    (is (= "closed" (datastar/toc-default presented)))
    (is (str/includes? (datastar/early-js "closed") "localStorage.getItem('clerk-navbar')"))
    (testing "notebooks without a toc don't render one"
      (let [presented (present-string "(ns datastar-test.no-toc)\n;; # Title")]
        (is (nil? (datastar/toc-default presented)))
        (is (not (str/includes? (html/->html-str (html/inspect {} presented)) "<clerk-toc")))))))

(deftest render-hiccup
  (testing "viewers link their server side rendering via a `:render-hiccup` symbol"
    (is (= 'nextjournal.clerk.html/render-coll (:render-hiccup v/vector-viewer)))
    (is (= #'html/render-coll (html/render-hiccup-fn {:nextjournal/viewer v/vector-viewer}))))
  (testing "`:render-hiccup` can be a fn, which processed viewers keep in metadata so they can be printed and hashed"
    (let [render (fn [{:nextjournal/keys [value]}] [:span.custom value])
          processed (v/process-viewer {:pred number? :render-hiccup render} {})]
      (is (not (contains? processed :render-hiccup)))
      (is (= render (html/render-hiccup-fn {:nextjournal/viewer processed})))
      (is (not (str/includes? (v/->edn processed) "#object")))
      (is (= "<span class=\"custom\">42</span>"
             (html/->html-str (html/inspect {} (v/present (v/with-viewer {:render-hiccup render} 42))))))))
  (testing "a default viewer with a replaced `:render-fn` isn't rendered by its `:render-hiccup`"
    (is (html/custom-render-fn? (assoc v/image-viewer :render-fn '(fn [blob] [:img]))))
    (is (nil? (html/render-hiccup-fn {:nextjournal/viewer (assoc v/image-viewer :render-fn '(fn [blob] [:img]))}))))
  (testing "unless it replaces the `:render-hiccup` as well"
    (is (not (html/custom-render-fn? (assoc v/result-viewer :render-fn '(fn [_ _] [:<>]) :render-hiccup `html/render-nothing))))))

(deftest long-strings
  (testing "highlighting very long strings doesn't overflow the stack"
    (let [code (str "(def x \"" (apply str (repeat 50000 "a\\\"b")) "\")")]
      (is (= code (apply str (map second (html/tokenize-clojure code))))))))

(deftest frontends
  (is (= :datastar+sci-react-island (webserver/frontend {})))
  (is (every? webserver/datastar? [{} {:frontend :datastar} {:frontend :datastar+sci-react-island}]))
  (is (not (webserver/datastar? {:frontend :sci-react})))
  (is (webserver/islands? {}))
  (is (not (webserver/islands? {:frontend :datastar})))
  (is (not (webserver/squint-islands? {})))
  (if (webserver/squint-supported?)
    (do (is (webserver/datastar? {:frontend :datastar-squint-island}))
        (is (webserver/squint-islands? {:frontend :datastar-squint-island})))
    (testing "`:datastar-squint-island` throws on Clojure 1.10"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"requires Clojure 1.11"
                            (webserver/frontend {:frontend :datastar-squint-island})))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid `:frontend`" (webserver/frontend {:frontend :datastar+sci}))))

(deftest datastar-without-islands
  (let [presented (present-string "(ns datastar-test.no-islands (:require [nextjournal.clerk :as clerk]))
(clerk/with-viewer '(fn [x] [:div x]) 42)")]
    (with-redefs [datastar/live-islands? (constantly false)]
      (testing "rendering the notebook shows a full page error"
        (let [out (html/->html-str (datastar/render-main {:presented presented} "tab"))]
          (is (str/includes? out "Clerk couldn&apos;t render this notebook"))
          (is (str/includes? out "needs the browser to render"))
          (is (not (str/includes? out "<clerk-island")))))
      (testing "the page doesn't reference the cljs viewer bundle"
        (is (not (str/includes? (datastar/page-html {:presented presented} "tab" {}) "clerk-viewer-js"))))
      (testing "presenting it throws so the error shows up at the REPL"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"needs the browser"
                              (datastar/throw-if-not-renderable! presented)))))
    (testing "static builds with `:frontend :datastar` throw"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"needs the browser"
                            (datastar/static-page-html {:frontend :datastar} {:presented presented}))))
    (testing "static builds with islands render them"
      (is (str/includes? (datastar/static-page-html {} {:presented presented}) "<clerk-island")))))

(deftest unqualified-synced-atoms
  (let [presented (present-string "(ns datastar-test.unqualified (:require [nextjournal.clerk :as clerk] [nextjournal.clerk.experimental :as cx]))
^{::clerk/sync true} (defonce !n (atom 3))
(cx/slider '!n)")]
    (is (= 'datastar-test.unqualified (html/doc-ns presented)))
    (testing "unqualified symbols resolve in the notebook's namespace so the slider renders on the server"
      (let [out (html/->html-str (datastar/render-static-doc {:islands? false} presented))]
        (is (re-find #"<input[^>]*type=\"range\"[^>]*value=\"3\"" out))))))

(defmacro when-squint
  "Runs `body` if Squint can be loaded.
  Asserts that the `:datastar-squint-island` frontend throws if not."
  [& body]
  `(if (webserver/squint-supported?)
     (do ~@body)
     (is (~'thrown-with-msg? clojure.lang.ExceptionInfo #"requires Clojure 1.11"
                             (webserver/frontend {:frontend :datastar-squint-island})))))

(deftest squint-islands
  (when-squint
    (let [presented (present-string "(ns datastar-test.squint (:require [nextjournal.clerk :as clerk]))
(clerk/with-viewer '(fn [x] [:div.text-red-500 (str \"Hello \" x)]) \"Squint\")")
          out (html/->html-str (html/inspect {:squint-islands? true} presented))
          module (second (re-find #"<clerk-squint-island[^>]* module=\"([^\"]+)\"" out))]
      (testing "a `:render-fn` renders as a `<clerk-squint-island>` with its value as json"
        (is module)
        (is (str/includes? out "value=\"&quot;Squint&quot;\"")))
      (testing "the module exports the compiled `:render-fn`"
        (let [js ((datastar/squint-fn 'module-js) module)]
          (is (str/includes? js "export { render_fn }"))
          (is (str/includes? js "\"div.text-red-500\""))))
      (with-redefs [datastar/live-squint-islands? (constantly true)]
        (testing "the page maps the modules' imports"
          (is (str/includes? (datastar/page-html {:presented presented} "tab" {}) "<script type=\"importmap\">")))
        (testing "the module is served"
          (is (= 200 (:status (datastar/app {:uri (str "/_clerk/squint/" module ".js")})))))
        (testing "Reagami and the Squint core are served"
          (is (str/includes? (:body (datastar/app {:uri "/_clerk/reagami.js"})) "export {"))
          (is (= 200 (:status (datastar/app {:uri "/_clerk/squint-core.js"})))))))))

(deftest squint-render-fn-forms
  (when-squint
    (testing "`clojure.core` symbols are unqualified"
      (is (= '(fn [x] (str x)) ((datastar/squint-fn 'prepare-form) '(fn [x] (clojure.core/str x))))))
    (testing "`#()` forms compile"
      (is (str/includes? ((datastar/squint-fn 'compile-render-fn) '#(vector :div %)) "export { render_fn }")))
    (testing "a `:render-fn` with symbols outside `clojure.core` and `js/` throws"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"`nextjournal.clerk.render/inspect` in a `:render-fn` isn't supported"
                            ((datastar/squint-fn 'compile-render-fn) '(fn [x] [nextjournal.clerk.render/inspect x])))))
    (testing "presenting a notebook with such a `:render-fn` throws so the error shows up at the REPL"
      (with-redefs [datastar/live-squint-islands? (constantly true)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"isn't supported by the `:datastar-squint-island` frontend"
                              (datastar/throw-if-not-renderable!
                               (present-string "(ns datastar-test.squint-unsupported (:require [nextjournal.clerk :as clerk]))
(clerk/with-viewer '(fn [x] [nextjournal.clerk.render/inspect x]) 1)"))))))))
