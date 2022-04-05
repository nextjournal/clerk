(ns nextjournal.clerk.browser-nrepl
  (:require [bencode.core :as bencode]
            [org.httpkit.server :as httpkit])
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
        msg (update-when msg "code" #(String. ^bytes %))]
    msg))

#_(defn send [^OutputStream os msg {:keys [debug-send]}]
  (when debug-send (prn "Sending" msg))
  (write-bencode os msg)
  (.flush os))

(defn send-response [{:keys [msg out response]}]
  (let [response (assoc response "id" (get msg "id"))]
    (prn :resp response)
    (bencode/write-bencode out response)
    (.flush ^java.io.OutputStream out)))

(defn handle-clone [ctx]
  (let [id (str (java.util.UUID/randomUUID))]
    ;; (swap! (:sessions ctx) (fnil conj #{}) id)
    (send-response (assoc ctx :response {"new-session" id "status" ["done"]}))))

(def nrepl-channel (atom nil))

(defn nrepl-eval [{:keys [id] :as ctx} s]
  (httpkit/send! @nrepl-channel (str {:op :eval
                                      :code s
                                      :id id}))
  (prn :eval)
  (send-response (assoc ctx :response {"status" ["done"]})))

(defn handle-eval [{:keys [msg] :as ctx}]
  (let [code (get msg "code")
        id (get msg "id")]
    (nrepl-eval (assoc ctx :id id) code)))

(defn session-loop [in out {:keys [opts]}]
  (loop []
    (when-let [msg (try
                     (let [msg (read-bencode in)]
                       msg)
                     (catch EOFException _
                       (when-not (:quiet opts)
                         (println "Client closed connection."))))]
      (prn :msg msg)
      (case (get msg "op")
        "clone" (handle-clone {:out out :msg msg})
        "eval" (handle-eval {:out out :msg msg})
        (println "Unhandled message" msg))
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
