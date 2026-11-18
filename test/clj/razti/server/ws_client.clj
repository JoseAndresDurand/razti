(ns razti.server.ws-client
  "Cliente Sente para la JVM sobre java.net.http.WebSocket del JDK
  (pruebas y smoke; sin org.java-websocket). TDD M2/S02 §4."
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [taoensso.sente :as sente]
            [taoensso.sente.interfaces :as i]
            [taoensso.sente.packers.transit :as transit])
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpResponse HttpResponse$BodyHandlers
                          WebSocket WebSocket$Builder WebSocket$Listener WebSocketHandshakeException)
           (java.time Duration)
           (java.util.concurrent CompletionException)))

(set! *warn-on-reflection* true)

(defn- http-client ^HttpClient []
  (-> (HttpClient/newBuilder) (.connectTimeout (Duration/ofSeconds 5)) (.build)))

(defn http-get
  "GET simple → {:status :body :headers (mapa en minúsculas → lista)}."
  [url]
  (let [^HttpResponse resp (.send (http-client)
                                  (-> (HttpRequest/newBuilder (URI. url)) (.GET) (.build))
                                  (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode resp)
     :body (str (.body resp))
     :headers (into {} (map (fn [[k v]] [(str/lower-case k) (vec v)]))
                    (.map (.headers resp)))}))

(defn fetch-session
  "GET / → {:cookie \"razti-session=…\" :token \"…\"}."
  [base-url]
  (let [{:keys [headers body]} (http-get (str base-url "/"))
        set-cookie (first (get headers "set-cookie"))]
    {:cookie (some-> set-cookie (str/split #";") first)
     :token (second (re-find #"name=\"csrf-token\" content=\"([^\"]+)\"" body))}))

(defn- ws-builder ^WebSocket$Builder [headers]
  (reduce-kv (fn [^WebSocket$Builder b k v] (.header b (str k) (str v)))
             (.newWebSocketBuilder (http-client))
             (or headers {})))

(defn handshake-status
  "Intenta el handshake WS: → :open o el código HTTP con que se rechaza."
  [ws-url headers]
  (try
    (let [^WebSocket ws (.join (.buildAsync (ws-builder headers) (URI. ws-url)
                                            (reify WebSocket$Listener)))]
      (.abort ws)
      :open)
    (catch CompletionException e
      (let [c (.getCause e)]
        (if (instance? WebSocketHandshakeException c)
          (.statusCode (.getResponse ^WebSocketHandshakeException c))
          (throw e))))))

(defn- ws-constructor [{:keys [uri-str headers on-message on-error on-close]}]
  (delay
    (let [buf (StringBuilder.)
          listener (reify WebSocket$Listener
                     (onOpen [_ ws] (.request ws 1))
                     (onText [_ ws data last?]
                       (.append buf data)
                       (when last?
                         (let [s (str buf)]
                           (.setLength buf 0)
                           (on-message s)))
                       (.request ws 1)
                       nil)
                     (onClose [_ _ws code reason]
                       (on-close code reason true)
                       nil)
                     (onError [_ _ws err] (on-error err)))
          ^WebSocket ws (.join (.buildAsync (ws-builder headers) (URI. uri-str) listener))]
      (reify i/IClientWebSocket
        (cws-raw [_] ws)
        (cws-send [_ data] (.join (.sendText ws ^CharSequence data true)) true)
        (cws-close [_ code reason _clean?]
          (.sendClose ws (int (or code 1000)) (str (or reason ""))))))))

(defn connect!
  "Cliente Sente por WebSocket contra base-url (http://host:puerto)."
  [base-url]
  (let [{:keys [cookie token]} (fetch-session base-url)
        uri (URI. base-url)
        host (cond-> (.getHost uri) (pos? (.getPort uri)) (str ":" (.getPort uri)))
        origin (str (.getScheme uri) "://" host)
        {:keys [ch-recv] :as c} (sente/make-channel-socket-client!
                                 "/chsk" token
                                 {:type :ws
                                  :protocol (keyword (.getScheme uri))
                                  :host host
                                  :packer (transit/get-packer)
                                  :headers {"Cookie" cookie "Origin" origin}
                                  :ws-constructor ws-constructor})
        events (atom [])]
    (async/thread
      (loop []
        (when-let [m (async/<!! ch-recv)]
          (swap! events conj (:event m))
          (recur))))
    (assoc c :events events)))

(defn await-pred
  "Espera hasta timeout-ms a que (pred) sea verdadero. → valor o nil."
  [pred timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (or (pred)
          (when (< (System/currentTimeMillis) deadline)
            (Thread/sleep 20)
            (recur))))))

(defn await-open [client timeout-ms]
  (await-pred #(:open? @(:state client)) timeout-ms))

(defn pushes
  "Eventos empujados por el servidor ([id datos]) recibidos hasta ahora."
  [client]
  (keep (fn [[id data]] (when (= id :chsk/recv) data)) @(:events client)))

(defn await-push
  "Primer evento empujado que cumple pred, esperando hasta timeout-ms."
  [client pred timeout-ms]
  (await-pred #(first (filter pred (pushes client))) timeout-ms))

(defn send! [client ev]
  ((:send-fn client) ev))

(defn request!
  "Envía ev y espera la respuesta del callback (o :timeout)."
  [client ev timeout-ms]
  (let [p (promise)]
    ((:send-fn client) ev timeout-ms #(deliver p %))
    (deref p (+ timeout-ms 500) :timeout)))

(defn close! [client]
  (sente/chsk-disconnect! (:chsk client))
  (async/close! (:ch-recv client)))
