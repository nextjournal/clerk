(ns playwright-tests
  {:clj-kondo/config '{:skip-comments false}}
  (:require ["child_process" :as cp]
            ["playwright$default" :refer [chromium]]
            [clojure.string :as str]
            [clojure.test :as t :refer [deftest is async use-fixtures]]
            [promesa.core :as p]
            [nbb.core :refer [await]]))

(def sha (or (first *command-line-args*)
             (str (cp/execSync "git rev-parse HEAD"))))

(def index (str/replace "https://snapshots.nextjournal.com/clerk/build/{{sha}}/index.html"
                        "{{sha}}" sha))

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

(defn test-notebook [page link]
  (println "Visiting" link)
  (p/do (goto page link)
        (is (-> (.locator page "div.viewer:has-text(\"Hello, Clerk\")")
                (.isVisible)))))

(def console-errors (atom []))

(deftest index-page-test
  (async done
         (-> (p/let [page (.newPage @browser)
                     _ (.on page "console"
                            (fn [msg]
                              (when (and (= "error" (.type msg))
                                         (not (str/ends-with?
                                               (.-url (.location msg)) "favicon.ico")))
                                (swap! console-errors conj msg))))
                     _ (goto page index)
                     _ (is (-> (.locator page "h1:has-text(\"Clerk\")")
                               (.isVisible #js {:timeout 10000})))
                     links (-> (.locator page "text=/.*\\.clj$/i")
                               (.allInnerTexts))
                     links (map (fn [link]
                                  (str index "#/" link)) links)]
               (p/run! #(test-notebook page %) links)
               (is (zero? (count @console-errors))
                   (str/join "\n" (map (fn [msg]
                                         [(.text msg) (.location msg)])
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

(defn -main [& _args]
  (t/test-vars (get-test-vars)))

(comment
  (launch-browser)
  (def p (await (.newPage @browser)))
  (.on p "console" (fn [msg]
                     (when (= "error" (.type msg))
                       (swap! console-errors conj msg))))
  (await (goto p "https://snapshots.nextjournal.com/clerk/build/549f9956870c69ef0951ca82d55a8e5ec2e49ed4/index.html"))
  (def loc (await (.locator p "text=/.*\\.clj$/i")))
  (def elt (await (.elementHandles loc #js {:timeout 1000})))
  (first elt)
  )
