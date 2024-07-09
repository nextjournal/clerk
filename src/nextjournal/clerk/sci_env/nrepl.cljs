(ns nextjournal.clerk.sci-env.nrepl
  (:require [sci.core :as sci]
            [sci.ctx-store :as store]
            [sci.nrepl.completions :refer [completions]]
            [sci.nrepl.info :refer [info]]))

;; copied & adapted from https://github.com/babashka/scittle/blob/691ed3a31a9fd7e794cb5bf302f4b693ed002c56/src/scittle/nrepl.cljs

(defonce !last-ns (volatile! @sci/ns))

(defn eval-string [s]
  (sci/binding [sci/ns @!last-ns]
    (let [rdr (sci/reader s)]
      (loop [res nil]
        (let [form (sci/parse-next (sci.ctx-store/get-ctx) rdr)]
          (if (= :sci.core/eof form)
            (do
              (vreset! !last-ns @sci/ns)
              res)
            (recur (sci/eval-form (sci.ctx-store/get-ctx) form))))))))

(defn nrepl-websocket []
  (.-ws_nrepl js/window))

(defn nrepl-reply [{:keys [id session]} {:keys [ns] :as payload}]
  (.send (nrepl-websocket)
         (str
          (let [ns (or ns (str @!last-ns))]
            (-> (assoc payload :id id :session session :ns ns)
                (dissoc :ctx))))))

(defn handle-nrepl-eval [{:keys [code] :as msg}]
  (let [[kind val] (try [::success (eval-string code)]
                        (catch :default e
                          [::error (str e)]))]
    (case kind
      ::success
      (do (nrepl-reply msg {:value (pr-str val)})
          (nrepl-reply msg {:status ["done"]}))
      ::error
      (do
        (nrepl-reply msg {:err (pr-str val)})
        (nrepl-reply msg {:ex (pr-str val)
                          :status ["error" "done"]})))))

(defn handle-nrepl-info [msg]
  (let [info (info (assoc msg :ctx (store/get-ctx)))]
    (nrepl-reply msg info)))

(declare ops)

(defn handle-describe [msg]
  (nrepl-reply
   msg
   {:versions {"clerk-render-nrepl" {"major" "0"
                                     "minor" "0"
                                     "incremental" "1"}}
    :ops (zipmap
          (map
           name
           (concat
            (keys ops)
            ;; sci.nrepl browser_server.clj handles:
            #{:clone :load-file}
            ;; we are lying about close?
            #{"close"}))
          (repeat {}))
    :status ["done"]}))

(def ops
  "Operations supported by the nrepl server."
  {:eval handle-nrepl-eval
   :info handle-nrepl-info
   :eldoc handle-nrepl-info
   :lookup handle-nrepl-info
   :describe handle-describe
   :complete (fn [msg] (let [completions (completions (assoc msg :ctx (store/get-ctx)))]
                         (nrepl-reply msg completions)))})

(defn handle-nrepl-message [msg]
  (if-let [handler (ops (:op msg))]
    (handler msg)
    (nrepl-reply msg (merge msg {:status ["error" "done"] :err "unkown-op"}))))
