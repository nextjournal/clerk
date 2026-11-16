;; # Custom elements
(ns datastar-elements
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.experimental :as cx]))

;; ## Leaflet
;; The map keeps its position when its `value` changes.
(clerk/defelement leaflet-map
  (extends ClerkElement)
  (field the-map nil)

  Object
  (^:async render [this {:keys [center zoom]}]
   (if the-map
     (.setZoom the-map zoom)
     (let [L (await (js/import "https://esm.sh/leaflet@1.9.4"))
           link (js/document.createElement "link")
           container (js/document.createElement "div")]
       (set! (.-rel link) "stylesheet")
       (set! (.-href link) "https://esm.sh/leaflet@1.9.4/dist/leaflet.css")
       (.setAttribute container "style" "height: 300px")
       (.append (.-shadowRoot this) link container)
       (set! the-map (-> (.map L container) (.setView center zoom)))
       (-> (.tileLayer L "https://tile.openstreetmap.org/{z}/{x}/{y}.png") (.addTo the-map)))))

  (disconnectedCallback [_this]
   (some-> the-map .remove)))

(def map-viewer
  {:transform-fn clerk/mark-presented
   :render-hiccup (fn [{:nextjournal/keys [value]}]
                    [leaflet-map {:value value}])})

^{::clerk/sync true}
(defonce !zoom (atom 12))

(cx/slider {:min 3 :max 18} '!zoom)

;; Pan the map, then move the slider:
(clerk/with-viewer map-viewer {:center [52.37 4.89] :zoom @!zoom})

;; ## Mermaid
(clerk/defelement mermaid-diagram
  (extends ClerkElement)

  Object
  (^:async render [this text]
   (let [mermaid (.-default (await (js/import "https://esm.sh/mermaid@11.4.1")))
         result (await (.render mermaid (str "mermaid-" (js/Date.now)) text))]
     (set! (.-innerHTML (.-shadowRoot this)) (.-svg result)))))

(def mermaid-viewer
  {:transform-fn clerk/mark-presented
   :render-hiccup (fn [{:nextjournal/keys [value]}]
                    [mermaid-diagram {:value value}])})

(clerk/with-viewer mermaid-viewer
  "graph LR
  A[defelement] --> B[Squint] --> C[ES module] --> D[customElements.define]")
