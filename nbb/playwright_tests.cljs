(ns playwright-tests
  (:require [cljs-bean.core :refer [bean]]
            [clojure.string :as str]
            [clojure.test :as t :refer [deftest is async use-fixtures]]
            [promesa.core :as p]))

(def sha (first *command-line-args*))
(def index (str/replace "https://snapshots.nextjournal.com/clerk/build/{{sha}}/index.html"
                        "{{sha}}" sha))

(def chromium
  (.-chromium (js/require "playwright")))

(def browser (atom nil))

(def default-launch-options
  (clj->js {:args ["--no-sandbox"]
            #_#_:headless false
            #_#_:devtools true}))


(defn launch-browser []
  (p/let [b (.launch chromium #js {:headless false})
          _ (reset! browser b)]))

(def close-browser true)

(use-fixtures :once
  {:before
   (fn []
     (async done
            (->
             (launch-browser)
             (.finally done))))
   :after
   (fn []
     (async done
            (if close-browser
              (p/let [_ (.close @browser)]
                (done))
              (done))))})

(defn goto [page url]
  (.goto page url #js{:waitUntil "networkidle"}))

(defn sleep [ms]
  (js/Promise.
   (fn [resolve]
     (js/setTimeout resolve ms))))

;; https://snapshots.nextjournal.com/clerk/build/549f9956870c69ef0951ca82d55a8e5ec2e49ed4/index.html

(deftest index-page-test
  (async done
         (-> (p/let [page (.newPage @browser)
                     _ (goto page index)
                     elt (-> (.locator page "text=Clerk")
                             (.elementHandle #js {:timeout 1000}))]
               (is elt))
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

(t/test-vars (get-test-vars))
