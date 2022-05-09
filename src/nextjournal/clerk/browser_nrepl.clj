(ns nextjournal.clerk.browser-nrepl
  (:require
   [bencode.core :as bencode]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [org.httpkit.server :as httpkit])
  (:import
   [java.io PushbackInputStream EOFException BufferedOutputStream]
   [java.net ServerSocket]))

(set! *warn-on-reflection* true)

(defn update-when [m k f]
  (if-let [v (get m k)]
    (assoc m k (f v))
    m))

(defn coerce-bencode [x]
  (if (bytes? x)
    (String. ^bytes x)
    x))

(defn read-bencode [in]
  (try (let [msg (bencode/read-bencode in)
             msg (zipmap (map keyword (keys msg))
                         (map coerce-bencode (vals msg)))]
         msg)
       (catch Exception e
         #_(def e e)
         (throw e))))

(def !last-ctx (volatile! nil))

(defn send-response [{:keys [out id session response]
                      :or {out (:out @!last-ctx)}}]
  (let [response (cond-> response
                   id (assoc :id id)
                   session (assoc :session session))]
    (bencode/write-bencode out response)
    (.flush ^java.io.OutputStream out)))

(defn handle-clone [ctx]
  (let [id (str (java.util.UUID/randomUUID))]
    (send-response (assoc ctx
                          :response {"new-session" id "status" ["done"]}))))

(defonce nrepl-channel (atom nil))

(defn response-handler [message]
  (let [msg (edn/read-string message)
        id (:id msg)
        session (:session msg)]
    (send-response {:id id
                    :session session
                    :response (dissoc (edn/read-string message)
                                      :id :session)})))

(defn handle-eval [{:keys [msg session id] :as ctx}]
  (vreset! !last-ctx ctx)
  (let [code (get msg :code)]
    (if (or (str/includes? code "clojure.main/repl-requires")
            (str/includes? code "System/getProperty"))
      (do
        (send-response (assoc ctx :response {"value" "nil"}))
        (send-response (assoc ctx :response {"status" ["done"]})))
      (when-let [chan @nrepl-channel]
        (httpkit/send! chan (str {:op :eval
                                  :code code
                                  :id id
                                  :session session}))))))

(defn handle-load-file [ctx]
  (let [msg (get ctx :msg)
        code (get msg :file)
        msg (assoc msg :code code)
        ctx (assoc ctx :msg msg)]
    (handle-eval ctx)))

(defn handle-complete [{:keys [id session msg]}]
  (when-let [chan @nrepl-channel]
    (let [symbol (get msg :symbol)
          prefix (get msg :prefix)
          ns (get msg :ns)]
      (httpkit/send! chan (str {:op :complete
                                :id id
                                :session session
                                :symbol symbol
                                :prefix prefix
                                :ns ns})))))

(defn handle-describe [ctx]
  (send-response (assoc ctx :response
                        {"status" #{"done"}
                         "ops" (zipmap #{"clone" "close" "eval"
                                         "load-file"
                                         "complete"
                                         "describe"
                                         ;; "ls-sessions"
                                         ;; "eldoc"
                                         ;; "info"
                                         ;; "lookup"
                                         }
                                       (repeat {}))
                         "versions" {"clerk-browser-nrepl" {"major" "0"
                                                            "minor" "0"
                                                            "incremental" "1"}}})))

(defn session-loop [in out {:keys [opts]}]
  (loop []
    (when-let [msg (try
                     (let [msg (read-bencode in)]
                       msg)
                     (catch EOFException _
                       (when-not (:quiet opts)
                         (println "Client closed connection."))))]
      (let [ctx {:out out :msg msg}
            id (get msg :id)
            session (get msg :session)
            ctx (assoc ctx :id id :session session)]
        (case (keyword (get msg :op))
          :clone (handle-clone ctx)
          :eval (handle-eval ctx)
          :describe (handle-describe ctx)
          :load-file (handle-load-file ctx)
          :complete (handle-complete ctx)
          (send-response (assoc ctx :response {"status" #{"error" "unknown-op" "done"}}))))
      (recur))))

(defn listen [^ServerSocket listener {:as opts}]
  (let [client-socket (.accept listener)
        in (.getInputStream client-socket)
        in (PushbackInputStream. in)
        out (.getOutputStream client-socket)
        out (BufferedOutputStream. out)]
    (future
      (session-loop in out {:opts opts}))
    (recur listener opts)))

(defonce !socket (atom nil))

(defn start-browser-nrepl! [{:keys [port]
                             :or {port 1339}}]
  (let [inet-address (java.net.InetAddress/getByName "localhost")
        socket (new ServerSocket port 0 inet-address)
        _ (reset! !socket socket)]
    (future (listen socket nil))))

(defn stop-browser-nrepl! []
  (.close ^ServerSocket @!socket))

(defn create-channel [req]
  (httpkit/as-channel req
                      {:on-open (fn [ch]
                                  (reset! nrepl-channel ch))
                       :on-close (fn [_ch _reason] (prn :close))
                       :on-receive
                       (fn [_ch message]
                         (response-handler message))}))

;;;; Scratch

(comment

  (require '[nextjournal.clerk :as clerk])
  (clerk/serve! {})
  (start-browser-nrepl! {:port 1339})
  (clerk/show! "notebooks/cljs_render_fn_file.clj")
  (stop-browser-nrepl!)

  )
