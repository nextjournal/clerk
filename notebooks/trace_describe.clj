(ns trace-describe
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.tap :as tap]))

^{::clerk/viewer clerk/hide-result}
(defn process-trace [trace]
  (-> trace
      (update-in [:opts :viewers] count)))

(defn traced-describe [x]
  (let [!trace (atom [])]
    (v/describe x {:trace-fn #(swap! !trace conj (process-trace %))})
    @!trace))

;; ---
^:nextjournal.clerk/no-cache
(traced-describe [1 2 3])

;; ---

^:nextjournal.clerk/no-cache
(-> (clerk/with-viewer tap/taps-viewer
      [{:tap (javax.imageio.ImageIO/read (java.net.URL. "file:/Users/mk/Desktop/CleanShot 2022-03-28 at 15.15.15@2x.png"))}])
    traced-describe
    clerk/code)

;; ---

^:nextjournal.clerk/no-cache
(let [!trace (atom [])]
  (v/describe
   (clerk/table [[(javax.imageio.ImageIO/read (java.net.URL. "file:/Users/mk/Desktop/CleanShot 2022-03-28 at 15.15.15@2x.png"))]])
   {:trace-fn #(swap! !trace conj (process-trace %)) :path [0 0]})
  (clerk/code @!trace))
