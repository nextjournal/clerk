(ns playwright-tests
  (:require [clojure.string :as str]
            [clojure.test :as t :refer [deftest is async use-fixtures]]
            [nbb.core :refer [*file*]]
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

(def headless (boolean (.-CI js/process.env)))

(defn launch-browser []
  (p/let [b (.launch chromium #js {:headless headless})
          _ (reset! browser b)]))

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

(defn test-notebook [page link]
  (println "Visiting" link)
  (p/let [_ (goto page link)
          _t (-> (.locator page "div.viewer")
                (.allInnerTexts))]
    (is true)))

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
                     elt (-> (.locator page "h1:has-text(\"Clerk\")")
                             (.elementHandle #js {:timeout 5000}))
                     _ (is elt)
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
             (.finally
              (fn []
                (p/let [#_#__ (sleep 15000)]
                  (done)))))))

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

(defmacro defp [name & body]
  `(-> (p/let [res (do ~@body)]
         (def ~name res))
       (.catch js/console.log)))

(comment
  (launch-browser)
  (defp p (.newPage @browser))
  (.on p "console" (fn [msg]
                     (when (= "error" (.type msg))
                       (swap! console-errors conj msg))))
  (goto p "https://dude.devx")
  (goto p "https://snapshots.nextjournal.com/clerk/build/549f9956870c69ef0951ca82d55a8e5ec2e49ed4/index.html")
  (defp loc (.locator p "text=/.*\\.clj$/i"))
  (defp elt (.elementHandles loc #js {:timeout 1000}))


  (require '[cljs-bean.core :refer [bean]])
  (require '[cljs.pprint :as pp])
  (pp/pprint (bean m :recursive true))
  (.type m)
  (require '[applied-science.js-interop :as j])
  (j/lookup m)
  (js/console.log m)
  )
