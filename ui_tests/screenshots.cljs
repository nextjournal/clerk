(ns screenshots
  {:clj-kondo/config '{:lint-as {promesa.core/let clojure.core/let}}}
  (:require ["playwright$default" :as pw :refer [chromium]]
            [promesa.core :as p]
            [nbb.core :refer [await]]
            [babashka.cli :as cli]))

(defn println+flush [& args]
  (apply println args)
  (flush))

(defn goto [page url]
  (.goto page url #js{:waitUntil "networkidle"}))

(comment
  "https://snapshots.nextjournal.com/clerk/build/48574b57e77d29b28cc158b9e747e943d15757d6/index.html#/notebooks/viewers/image_layouts.clj"
  "https://snapshots.nextjournal.com/clerk/build/48574b57e77d29b28cc158b9e747e943d15757d6/index.html#/notebooks/rule_30.clj")

(def browser (await (.launch chromium #js {:headless false})))

(def page-width 1280)
(def page-height 720)

(defn new-page [url]
  (when-not url
    (throw (ex-info "⚠️ Must provide an URL." {})))
  (println+flush "🌍 Visiting page:" url)
  (p/let [page (.newPage browser #js {:viewport #js {:width page-width :height page-height}})
          _ (goto page url)]
    page))

(defn ->path [out-dir filename]
  (cond->> filename
    out-dir (str out-dir "/")))



(defn screenshot
  ([page] (screenshot {} page))
  ([{:keys [out-dir]} page]
   (println+flush "📷 Starting screenshotting…")
   (p/let [results (.locator page ".viewer-result")
           results-count (.count results)]
     (println+flush "📸 Screenshotting page with bounds" (str page-width "×" page-height))
     (.screenshot page #js {:path (->path out-dir "page.png")})
     (p/loop [i 0]
       (if (< i results-count)
         (p/let [res (.nth results i)
                 bounds (.boundingBox res)]
           (if (<= 250 (.-height bounds))
             (p/let [_ (println+flush "📸 Screenshotting result with bounds" (str (.-width bounds) "×" (.-height bounds)))
                     buffer (.screenshot res #js {:path (->path out-dir (str "result-" (inc i) ".png"))})
                     base64 (.toString buffer "base64")
                     image-uri (str "data:image/png;base64," base64)
                     _ (.evaluate res "console.log(nextjournal.clerk.sci_viewer)")
                     _ (.evaluate res (str "nextjournal.clerk.sci_viewer.append_trimmed_image(\"" image-uri "\", \"res-" i "\")"))]
               (js/console.log "APPENDED" (.locator page (str "#res-" i))))
             (println+flush "🦘 Skipping result with bounds" (str (.-width bounds) "×" (.-height bounds))))
           (p/recur (inc i)))
         (println+flush "✅ Done."))))))

(defn -main [& args]
  (p/let [{:as opts :keys [url]} (:opts (cli/parse-args args {:alias {:u :url :o :out-dir}}))
          page (new-page url)]
    (p/do
      (screenshot opts page)
      #_(.close browser))))

(comment
  (def page (new-page "http://localhost:7777"))
  (screenshot page))
