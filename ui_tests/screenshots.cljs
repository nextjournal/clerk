(ns screenshots
  {:clj-kondo/config '{:lint-as {promesa.core/let clojure.core/let}}}
  (:require ["playwright$default" :as pw :refer [chromium]]
            [promesa.core :as p]
            [nbb.core :refer [await]]))

(defn println+flush [& args]
  (apply println args)
  (flush))

(defn goto [page url]
  (.goto page url #js{:waitUntil "networkidle"}))

(def image-layouts-notebook "https://snapshots.nextjournal.com/clerk/build/48574b57e77d29b28cc158b9e747e943d15757d6/index.html#/notebooks/viewers/image_layouts.clj")

(def rule30-notebook "https://snapshots.nextjournal.com/clerk/build/48574b57e77d29b28cc158b9e747e943d15757d6/index.html#/notebooks/rule_30.clj")

(def url image-layouts-notebook)
(def browser (await (.launch chromium #js {:headless false})))
(def page-width 1280)
(def page-height 720)
(def page (await (.newPage browser #js {:viewport #js {:width page-width :height page-height}})))

(println+flush "🌍 Visiting page:" url)
(await (goto page url))

(defn screenshot []
  (println+flush "📷 Starting screenshotting…")
  (await (-> (p/let [results (.locator page ".viewer-result")
                     results-count (.count results)]
               (println+flush "📸 Screenshotting page with bounds" (str page-width "×" page-height))
               (.screenshot page #js {:path "page.png"})
               (p/loop [i 0]
                 (if (< i results-count)
                   (p/let [res (.nth results i)
                           bounds (.boundingBox res)]
                     (if (<= 250 (.-height bounds))
                       (do
                         (println+flush "📸 Screenshotting result with bounds" (str (.-width bounds) "×" (.-height bounds)))
                         (.screenshot res #js {:path (str "result-" (inc i) ".png")}))
                       (println+flush "🦘 Skipping result with bounds" (str (.-width bounds) "×" (.-height bounds))))
                     (p/recur (inc i)))
                   (println+flush "✅ Done.")))))))

(screenshot)

(.close browser)
