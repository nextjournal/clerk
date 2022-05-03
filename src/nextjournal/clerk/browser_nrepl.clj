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

(def known-keys ["id" "op" "code" "session" "file" "file-name" "file-path"])

(defn read-bencode [in]
  (try (let [msg (bencode/read-bencode in)
            _ (def m1 msg)
            msg (reduce (fn [msg k]
                          (def m msg)
                          (update-when msg k #(String. ^bytes %)))
                        msg known-keys)]
         msg)
       (catch Exception e
         (def e e)
         (throw e))))

(comment
  
  )

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
  (prn :message-handler (edn/read-string message))
  (let [msg (edn/read-string message)
        id (:id msg)
        session (:session msg)]
    (send-response {:id id
                    :session session
                    :response (dissoc (edn/read-string message)
                                      :id :session)}))
  #_(prn :message message))

(defn handle-eval [{:keys [msg session id] :as ctx}]
  (vreset! !last-ctx ctx)
  (let [code (get msg "code")]
    (def c code)
    (prn :code code)
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
        code (get msg "file")
        msg (assoc msg "code" code)
        ctx (assoc ctx :msg msg)]
    (handle-eval ctx)))

(defn handle-describe [ctx]
  (send-response (assoc ctx :response {"status" #{"done"}
                                       "ops" (zipmap #{"clone" "close" "eval"
                                                       ;; "load-file"
                                                       ;; "complete"
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
                     (prn :reading)
                     (let [msg (read-bencode in)]
                       msg)
                     (catch EOFException _
                       (when-not (:quiet opts)
                         (println "Client closed connection."))))]
      (prn :msg msg)
      (let [ctx {:out out :msg msg}
            id (get msg "id")
            session (get msg "session")
            ctx (assoc ctx :id id :session session)]
        (case (get msg "op")
          "clone" (handle-clone ctx)
          "eval" (handle-eval ctx)
          "describe" (handle-describe ctx)
          ;; "load-file" (handle-load-file ctx)
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

  (start-browser-nrepl! {:port 1339})
  (stop-browser-nrepl!)

  )
