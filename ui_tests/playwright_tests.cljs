(ns playwright-tests
  {:clj-kondo/config '{:skip-comments false}}
  (:require ["playwright$default" :refer [chromium]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :as t :refer [deftest is async use-fixtures]]
            [nbb.core :refer [await]]
            [promesa.core :as p]))

(defonce !opts (atom nil))

(def browser (atom nil))

(def default-launch-options
  (clj->js {:args ["--no-sandbox"]}))

(def headless (boolean (.-CI js/process.env)))

(defn launch-browser []
  (p/->> (.launch chromium #js {:headless headless})
         (reset! browser)))

(def close-browser true)

(use-fixtures :once
  {:before
   (fn []
     (async done
       (->
        (launch-browser)
        (.catch js/console.log)
        (.finally done))))
   :after
   (fn []
     (async done
       (if close-browser
         (p/do
           (.close @browser)
           (done))
         (done))))})

(defn goto [page url]
  (.goto page url #js{:waitUntil "networkidle"}))

;; https://snapshots.nextjournal.com/clerk/build/549f9956870c69ef0951ca82d55a8e5ec2e49ed4/index.html

(def console-errors (atom []))

(def page-selectors {"notebooks/rule_30.clj" "h1:has-text(\"Rule 30\")"
                     "notebooks/viewers/katex.clj" "span.katex"})

(defn test-notebook
  ([page url]
   (println "Visiting" url)
   (p/do (goto page url)
         (.waitForLoadState page "networkidle")
         (p/let [selector (or (:selector @!opts)
                              "div")
                 _ (prn :selector selector)
                 loc (.locator page selector #js {:timeout 10000})
                 loc (.first loc #js {:timeout 10000})
                 _ (.waitFor loc #js {:state "visible"})
                 visible? (.isVisible loc)]
           (is visible?))))
  ;; called from index-page-test
  ([page url link]
   (p/let [txt (.innerText link)
           selector (or (get page-selectors txt)
                        "div")]
     (println "Visiting" (str url "#/" txt))
     (p/do (.click link)
           (p/let [loc (.locator page selector)
                   loc (.first loc #js {:timeout 10000})
                   _ (.waitFor loc #js {:state "visible"})
                   visible? (.isVisible loc)]
             (is visible?))))))

(deftest index-page-test
  (async done
    (-> (p/let [page (.newPage @browser)
                _ (.on page "console"
                       (fn [msg]
                         (when (and (= "error" (.type msg))
                                    (not (str/ends-with?
                                          (.-url (.location msg)) "favicon.ico")))
                           (swap! console-errors conj {:msg msg :notebook (.url page)}))))
                _ (.on page "pageerror"
                       (fn [msg]
                         (swap! console-errors conj {:msg msg :notebook (.url page)})))]
          (let [{:keys [index url]} @!opts]
            (if (false? index)
              (test-notebook page url)
              (-> (p/let [_ (goto page url)
                          _ (is (-> (.locator page "h1:has-text(\"Clerk\")")
                                    (.isVisible #js {:timeout 10000})))
                          links (-> (.locator page "text=/.*\\.clj$/i")
                                    (.all))
                          _ (is (pos? (count links)))
                          #_#_links (filter (fn [link]
                                              (str/includes? link "cherry")) links)]
                    (p/run! #(p/do (test-notebook page url %)
                                   (.goBack page)) links)))))
          (p/delay 30000) ;; allow errors to be logged to console
          (is (zero? (count @console-errors))
              (str/join "\n" (map (fn [{:keys [msg notebook]}]
                                    [msg notebook])
                                  @console-errors))))
        (.catch (fn [err]
                  (js/console.log err)
                  (is false)))
        (.finally done))))

(defn index-link-href [page]
  (p/let [_ (.waitFor (.first (.locator page "a:text-is(\"Index\")")) #js {:timeout 10000})]
    (.evaluate page "[...document.querySelectorAll('a')].find(a => a.textContent.trim() === 'Index').href")))

(defn router-marker [page]
  (.evaluate page "window.routerMarker === true"))

(deftest router-navigation-test
  (async done
    (-> (p/let [{:keys [index url]} @!opts]
          (when-not (false? index)
            (p/let [page (.newPage @browser)
                    errors (atom [])
                    _ (.on page "pageerror" #(swap! errors conj %))
                    _ (.on page "console" (fn [msg]
                                            (when (= "error" (.type msg))
                                              (swap! errors conj (.text msg)))))
                    _ (goto page url)
                    fetch-edn? (.evaluate page "Boolean(history.state && history.state.edn_path)")]
              (when fetch-edn?
                (p/let [root (str/replace url #"index\.html$" "")
                        _ (is (= root (.url page)) "load replaces index.html with the build root")
                        link (.first (.locator page "text=/.*\\.clj$/i"))
                        link-text (.innerText link)
                        doc-path (str/replace link-text #"\.cljc?$" "")
                        page-url (str root doc-path "/")
                        _ (.evaluate page "window.routerMarker = true")
                        _ (.click link)
                        _ (.waitForURL page page-url #js {:timeout 10000})
                        routed? (router-marker page)
                        _ (is routed? "left-click inside the build uses the router")
                        href (index-link-href page)
                        _ (is (= root href) "Index link after left-click points to the build root")
                        _ (.goBack page)
                        _ (.waitForURL page root #js {:timeout 10000})
                        _ (.waitFor (.first (.locator page "h1:has-text(\"Clerk\")")) #js {:timeout 10000})
                        _ (goto page page-url)
                        href (index-link-href page)
                        _ (is (= root href) "Index link after reload points to the build root")
                        _ (is (empty? @errors) (str/join "\n" @errors))
                        outside-url (.-href (js/URL. "../" root))
                        _ (.evaluate page (str "window.routerMarker = true;"
                                               "var a = document.createElement('a');"
                                               "a.id = 'outside-link'; a.textContent = 'outside';"
                                               "a.href = '" outside-url "';"
                                               "document.body.appendChild(a)"))
                        _ (.click (.locator page "#outside-link"))
                        _ (.waitForURL page outside-url #js {:timeout 10000})
                        routed? (router-marker page)]
                  (is (not routed?) "link outside the build loads a new page"))))))
        (.catch (fn [err]
                  (js/console.log err)
                  (is false)))
        (.finally done))))

(defmethod t/report [:cljs.test/default :begin-test-var] [m]
  (println "===" (-> m :var meta :name))
  (println))

(defn print-summary []
  (t/report (assoc (:report-counters (t/get-current-env)) :type :summary)))

(defmethod t/report [:cljs.test/default :end-test-vars] [_]
  (let [env (t/get-current-env)
        counters (:report-counters env)
        failures (:fail counters)
        errors (:error counters)]
    (when (or (pos? failures)
              (pos? errors))
      (set! (.-exitCode js/process) 1))
    (print-summary)))

(defn get-test-vars []
  (->> (ns-publics 'playwright-tests)
       vals
       (filter (comp :test meta))))

(defn args-map->index [{:keys [sha url] :as opts}]
  (assoc opts
         :url
         (cond
           sha (str/replace "https://snapshots.nextjournal.com/clerk/build/{{sha}}/index.html" "{{sha}}" sha)
           url url)))

(defn -main [args-map-str]
  (let [opts (edn/read-string args-map-str)
        opts (args-map->index opts)]
    (reset! !opts opts)
    (prn opts)
    (prn :url (:url @!opts))
    (t/test-vars (get-test-vars))))

(comment
  (await (launch-browser))
  (def p (await (.newPage @browser)))
  (.on p "console" (fn [msg]
                     (when (= "error" (.type msg))
                       (swap! console-errors conj msg))))
  (def url "https://snapshots.nextjournal.com/clerk/build/c617e6fae2734a75ef4c53b5c410a76cc0a52160/index.html")
  (await (goto p url))
  (def loc (.first (.locator p "text=Clerk")))
  (await (.isVisible loc #js {:timeout 1000}))
  (def links (await (-> (.locator p "text=/.*\\.clj$/i")
                        #_(.allInnerTexts)
                        (.all))))
  (await (.click (second links)))
  (def links (map (fn [link]
                    (str url "#/" link)) links))
  (goto p "https://snapshots.nextjournal.com/clerk/book/39b8e38e26c11555fedc7e3bcc678a1d82354c91/book/index.html")
  (def page p)
  (await (p/let [selector "a[href$='#book-of-clerk']"
                loc (.locator page selector)
                 _ (def x loc)]
           (.isVisible loc #js {:timeout 20000})
           ))
  x
  (await (.innerText x))
  )
