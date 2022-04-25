(ns nextjournal.clerk.browser-nrepl
  (:require [bencode.core :as bencode]
            [org.httpkit.server :as httpkit]
            [clojure.edn :as edn])
  (:import [java.io InputStream PushbackInputStream EOFException BufferedOutputStream PrintWriter BufferedWriter Writer StringWriter]
           [java.net ServerSocket]
           ))

(set! *warn-on-reflection* true)

(defn update-when [m k f]
  (if-let [v (get m k)]
    (assoc m k (f v))
    m))

(defn read-bencode [in]
  (let [msg (bencode/read-bencode in)
        msg (update msg "id" #(String. ^bytes %))
        msg (update msg "op" #(String. ^bytes %))
        msg (update-when msg "code" #(String. ^bytes %))
        msg (update-when msg "session" #(String. ^bytes %))]
    (prn :msssg msg)
    msg))

#_(defn send [^OutputStream os msg {:keys [debug-send]}]
  (when debug-send (prn "Sending" msg))
  (write-bencode os msg)
  (.flush os))

(def !last-ctx (volatile! nil))

#_(let [sw (java.io.ByteArrayOutputStream.)]
  (bencode/write-bencode sw {:a 1 "a" 2})
  (let [bencode (str sw)]
    (bencode/read-bencode (java.io.PushbackInputStream. (java.io.ByteArrayInputStream. (.getBytes bencode))))))

(defn send-response [{:keys [out id session response]
                      :or {out (:out @!last-ctx)}}]
  (prn :pre-resp response)
  (let [response (cond-> response
                   id (assoc :id id)
                   session (assoc :session session))]
    (prn :resp response)
    #_(prn :res2 (let [sw (java.io.StringWriter.)]
                 (bencode/write-bencode sw response)
                 (str sw)))
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
    (when-let [chan @nrepl-channel]
      (httpkit/send! chan (str {:op :eval
                                :code code
                                :id id
                                :session session})))))

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
                                       "versions" []})))

(defn session-loop [in out {:keys [opts]}]
  (loop []
    (when-let [msg (try
                     (prn :reading)
                     (let [msg (read-bencode in)]
                       msg)
                     (catch EOFException _
                       (when-not (:quiet opts)
                         (println "Client closed connection."))))]
      (let [ctx {:out out :msg msg}
            id (get msg "id")
            session (get msg "session")
            ctx (assoc ctx :id id :session session)]
        (case (get msg "op")
          "clone" (handle-clone ctx)
          "eval" (handle-eval ctx)
          "describe" (handle-describe ctx)
          (println "Unhandled message" msg)))
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

(def !socket (atom nil))

(defn start-browser-nrepl! []
  (let [inet-address (java.net.InetAddress/getByName "localhost")
        socket (new ServerSocket 1339 0 inet-address)
        _ (reset! !socket socket)]
    (future (listen socket nil))))

(defn stop-browser-nrepl! []
  (.close ^ServerSocket @!socket))

;;;; Scratch

(comment

  (start-browser-nrepl!)
  (stop-browser-nrepl!)

  )
