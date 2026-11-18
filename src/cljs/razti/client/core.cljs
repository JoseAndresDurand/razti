(ns razti.client.core
  "Entrada del cliente. S02 paso 2: conexión y barra de estado (esqueleto WAN)."
  (:require [razti.client.ws :as ws]))

(defonce app (atom {:screen :lobby
                    :conn {:open? false :type nil :echo-ms nil}
                    :room nil
                    :snapshot nil
                    :grid nil
                    :me nil
                    :error nil}))

(defn- el [id] (.getElementById js/document id))

(defn status-text
  "T21: tipo de conexión y eco."
  [{:keys [open? type echo-ms]}]
  (if-not open?
    "Desconectado"
    (str "Conexión: " (if (= type :ws) "WebSocket" "Ajax (sin WebSocket)")
         (when echo-ms (str " · eco " echo-ms " ms")))))

(defn- render-status! [state]
  (set! (.-textContent (el "status")) (status-text (:conn state))))

(defn- on-push [_ev]
  ;; S02 paso 4: [:razti/room] [:razti/snapshot] [:razti/error]
  nil)

(defn init []
  (add-watch app ::status (fn [_ _ _ s] (render-status! s)))
  (render-status! @app)
  (ws/connect! app on-push))
