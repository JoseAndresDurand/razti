(ns razti.client.ws
  "Cliente Sente: conexión, eco (T21) y eventos del servidor."
  (:require [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as transit]))

(defonce ^:private conn (atom nil))

(defn- csrf-token []
  (some-> (.querySelector js/document "meta[name=csrf-token]")
          (.getAttribute "content")))

(defn send!
  "Envía un evento al servidor. Con timeout y callback espera respuesta."
  ([ev]
   (when-let [f (:send-fn @conn)] (f ev)))
  ([ev timeout-ms cb]
   (when-let [f (:send-fn @conn)] (f ev timeout-ms cb))))

(defn- now-ms []
  (js/Math.round (js/performance.now)))

(defn- echo! [app]
  (let [t (now-ms)]
    (send! [:razti/echo {:t t}] 5000
           (fn [reply]
             (when (and (sente/cb-success? reply) (= t (:t reply)))
               (swap! app assoc-in [:conn :echo-ms] (- (now-ms) t)))))))

(defn- handle! [app on-push {:keys [id ?data]}]
  (case id
    :chsk/state (let [[_ new-state] ?data]
                  (swap! app assoc :conn {:open? (boolean (:open? new-state))
                                          :type (:type new-state)
                                          :echo-ms nil})
                  (when (:open? new-state) (echo! app)))
    :chsk/recv (on-push ?data)
    nil))

(defn connect!
  "Abre el canal (:auto: WebSocket, o Ajax si la red no lo permite)."
  [app on-push]
  (let [{:keys [ch-recv] :as c} (sente/make-channel-socket-client!
                                 "/chsk" (csrf-token)
                                 {:type :auto :packer (transit/get-packer)})]
    (reset! conn c)
    (sente/start-client-chsk-router! ch-recv #(handle! app on-push %))))
