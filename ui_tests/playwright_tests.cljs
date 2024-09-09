(ns playwright-tests
  {:clj-kondo/config '{:skip-comments false}}
  (:require ["playwright$default" :refer [chromium]]
            ["@playwright/test$default" :refer [expect]]
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

(defn test-notebook
  ([page url]
   (println "Visiting" url)
   (p/do (goto page url)
         (.waitForLoadState page "networkidle")
         (p/let [selector (or (:selector @!opts) "div")
                 loc (.locator page selector)
                 loc (.first loc #js {:timeout 10000})
                 visible? (.toBeVisible (expect loc) #js {:timeout 10000})]
           (prn visible?)
           (is visible?))))
  ([page url link]
   (p/let [txt (.innerText link)]
     (println "Visiting" (str url "#/" txt))
     (p/do (.click link)
           (p/let [loc (.locator page "div")
                   loc (.first loc #js {:timeout 10000})
                   visible? (.isVisible loc #js {:timeout 10000})]
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
          #_(p/delay 30000)
          (is (zero? (count @console-errors))
              (str/join "\n" (map (fn [{:keys [msg notebook]}]
                                    [(.text msg) (.location msg) notebook])
                                  @console-errors))))
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
  (goto p "https://snapshots.nextjournal.com/clerk/build/c617e6fae2734a75ef4c53b5c410a76cc0a52160/index.html#/notebooks/cards.clj")
  )
