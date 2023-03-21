(ns screenshots
  "Playwright script to Generate open graph preview images from a notebook's results.

  Run this via:

      cd ui_tests; yarn nbb -m screenshots --url http://localhost:7777 --out-dir screenshots

  For a REPL development workflow start a nbb nrepl server via:

      cd ui_tests; yarn nbb nrepl-server :port 1337"

  {:clj-kondo/config '{:lint-as {promesa.core/let clojure.core/let}}}
  (:require ["playwright$default" :as pw :refer [chromium]]
            [clojure.edn :as edn]
            [promesa.core :as p]
            [nbb.core :refer [await]]
            [babashka.cli :as cli]))

(defn println+flush [& args]
  (apply println args)
  (flush))

(defn goto [page url]
  (when-not url
    (throw (ex-info "⚠️ Must provide an URL." {})))
  (println+flush "🌍 Visiting page:" url)
  (.goto page url #js{:waitUntil "networkidle"}))

(def browser (await (.launch chromium #js {:headless false})))

(def page-width 1280)
(def page-height 720)

(defn new-page []
  (p/let [ctx (.newContext browser #js {:deviceScaleFactor 2})]
    (.newPage ctx #js {:viewport #js {:width page-width :height page-height}})))

(defn ->path [out-dir filename]
  (cond->> filename
    out-dir (str out-dir "/")))

(defn screenshot
  ([page] (screenshot {} page))
  ([{:keys [out-dir]} page]
   (println+flush "📷 Starting screenshotting…")
   (p/let [og-captures (.locator page ".open-graph-image-capture")
           page (.addStyleTag page #js {:content ".sticky-table-header { display: none !important; box-shadow: none !important;}"})
           og-captures-count (.count og-captures)
           results (if (< 0 og-captures-count)
                     og-captures
                     (.locator page ".result-viewer"))
           results-count (.count results)]
     (println+flush "📸 Screenshotting page with bounds" (str page-width "×" page-height) "results: " results-count)
     (.screenshot page #js {:path (->path out-dir "page.png")})
     (p/loop [i 0]
       (if (< i results-count)
         (p/let [res (.nth results i)
                 bounds (.boundingBox res)]
           (if true #_ (<= 250 (.-height bounds))
               (p/let [id (.getAttribute res "data-block-id")
                       imgs (.locator res "img")
                       imgs-count (.count imgs)
                       single-image? (= imgs-count 1)
                       _ (println+flush (str "🔍 Result #" (inc i) " contains " imgs-count " " (if single-image? "image" "images") "."))
                       subject (if single-image? (.first imgs) res)
                       _ (println+flush (str "📸 Screenshotting result #" (inc i) " - ID: " id
                                             " (" (if single-image? "single image" "entire result") ")"
                                             " with bounds " (.-width bounds) "×" (.-height bounds)))
                       buffer (.screenshot subject)
                       base64 (.toString buffer "base64")
                       image-uri (str "data:image/png;base64," base64)
                       _ (.evaluate res (str "nextjournal.clerk.trim_image.append_trimmed_image("
                                             (pr-str image-uri) "," (pr-str (str "res-" i)) ")"))
                       trimmed-res (.locator page (str "#res-" i))
                       trimmed-bounds (.boundingBox trimmed-res)]
                 (println+flush (str "🔪 Trimming result #" (inc i) " to bounds " (.-width trimmed-bounds) "×" (.-height trimmed-bounds)))
                 (.screenshot trimmed-res #js {:path (->path out-dir (str (if id (name (edn/read-string id)) (str "result-" (inc i))) ".png"))}))
               (println+flush "🦘 Skipping result with bounds" (str (.-width bounds) "×" (.-height bounds))))
           (p/recur (inc i)))
         (println+flush "✅ Done."))))))

(defn -main [& args]
  (p/let [{:as opts :keys [url]} (:opts (cli/parse-args args {:alias {:u :url :o :out-dir}}))
          page (new-page)]
    (let [loaded? (.waitForEvent page "load")]
      (p/do
        (goto page url)
        loaded?
        (screenshot opts page)
        (.close browser)))))

(comment
  (def page (new-page))
  (goto page "http://localhost:7777")
  (screenshot page))
