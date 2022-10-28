(ns cherry-playwright
  {:clj-kondo/config '{:skip-comments false}}
  (:require ["child_process" :as cp]
            ["playwright$default" :refer [chromium]])
  (:require-macros [cherry-macros :refer [assert!]]))

(def sha (.trim (str (cp/execSync "git rev-parse HEAD"))))

(def index (.replace "https://snapshots.nextjournal.com/clerk/build/{{sha}}/index.html"
                     "{{sha}}" sha))

(def browser (atom nil))

(def default-launch-options
  (clj->js {:args ["--no-sandbox"]}))

;; TODO: boolean
(def headless (some? (.-CI js/process.env)))

(defn ^:async launch-browser []
  (let [chrome (js/await (.launch chromium #js {:headless headless}))]
    (reset! browser chrome)))

(def close-browser true)

(defn goto [page url]
  (.goto page url #js{:waitUntil "networkidle"}))

;; https://snapshots.nextjournal.com/clerk/build/549f9956870c69ef0951ca82d55a8e5ec2e49ed4/index.html

(defn ^:async test-notebook [page link]
  (println "Visiting" link)
  (js/await (goto page link))
  (println "Opened link")
  (-> (.locator page "div")
      (js/await)
      .first
      (.isVisible)
      js/await
      (assert! "should be true")))

(def console-errors (atom []))

(defn ^:async index-page-test []
  (try
    (let [page (js/await (.newPage @browser))
          _ (.on page "console"
                 (fn [msg]
                   (when (and (= "error" (.type msg))
                              (not (.endsWith
                                    (.-url (.location msg)) "favicon.ico")))
                     (swap! console-errors conj msg))))
          _ (js/await (goto page index))
          _ (js/await (-> (.locator page "h1:has-text(\"Clerk\")")
                          (.isVisible)))
          links (-> (.locator page "text=/.*\\.clj$/i")
                    (.allInnerTexts)
                    (js/await))
          links (map (fn [link]
                       (str index "#/" link)) links)]
      (doseq [l (rest links)]
        (js/await (test-notebook page l)))
      (when-not (zero? (count @console-errors))
        (apply str (interleave (map (fn [msg]
                                      [(.text msg) (.location msg)])
                                    @console-errors)
                               "\n"))))
    (catch :default err
      (js/console.log err))))

(try
  (js/await (launch-browser))
  (js/await (index-page-test))
  (finally
    (.close @browser)))
