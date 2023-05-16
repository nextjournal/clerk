(ns playwright-tests
  {:clj-kondo/config '{:skip-comments false}}
  (:require ["playwright$default" :refer [chromium]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :as t :refer [deftest is async use-fixtures]]
            [nbb.core :refer [await]]
            [promesa.core :as p]))

(defonce !index (atom nil))

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

(defn test-notebook [page link]
  (println "Visiting" link)
  (p/do (goto page link)
        (p/delay 500)
        (p/let [loc (.locator page "div")
                loc (.first loc)
                visible? (.isVisible loc)]
          (is visible?))))

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
                              (swap! console-errors conj {:msg msg :notebook (.url page)})))
                     _ (goto page @!index)
                     _ (is (-> (.locator page "h1:has-text(\"Clerk\")")
                               (.isVisible #js {:timeout 10000})))
                     links (-> (.locator page "text=/.*\\.clj$/i")
                               (.allInnerTexts))
                     _ (is (pos? (count links)))
                     links (map (fn [link]
                                  (str @!index "#/" link)) links)
                     links (filter (fn [link]
                                     (str/includes? link "cherry")) links)]
               (p/run! #(test-notebook page %) links)
               (p/delay 30000)
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

(defn args-map->index [{:keys [sha url]}]
  (cond
    sha (str/replace "https://snapshots.nextjournal.com/clerk/build/{{sha}}/index.html" "{{sha}}" sha)
    url url))

(defn -main [args-map-str]
  (prn :url (reset! !index (args-map->index (edn/read-string args-map-str))))
  (t/test-vars (get-test-vars)))

(comment
  (await (launch-browser))
  (def p (await (.newPage @browser)))
  (.on p "console" (fn [msg]
                     (when (= "error" (.type msg))
                       (swap! console-errors conj msg))))
  (await (goto p "https://snapshots.nextjournal.com/clerk/build/549f9956870c69ef0951ca82d55a8e5ec2e49ed4/index.html"))
  (def loc (.first (.locator p "text=Clerk")))
  (await (.isVisible loc #js {:timeout 1000}))
  )
