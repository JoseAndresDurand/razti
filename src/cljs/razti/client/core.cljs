(ns razti.client.core
  "Entrada del cliente: estado, vistas (lobby / sala / juego / resultado) y
  acciones (TDD M2/S02 §4). Textos con textContent, nunca innerHTML."
  (:require [razti.client.input :as input]
            [razti.client.render :as render]
            [razti.client.ws :as ws]
            [razti.shared.config :as config]))

(defonce app (atom {:screen :lobby
                    :conn {:open? false :type nil :echo-ms nil}
                    :room nil
                    :snapshot nil
                    :grid nil
                    :error nil
                    :debug-grid? false}))

(def ^:private error-messages
  {:room-full "La sala está llena (4 jugadores)."
   :room-not-found "No existe una sala con ese código."
   :name-taken "Ese nombre ya está en la sala."
   :game-in-progress "La partida ya empezó."
   :server-full "El servidor está lleno; prueba más tarde."
   :already-in-room "Ya estás en una sala."
   :not-enough-players "Se necesitan al menos 2 jugadores."
   :room-closed "La sala se cerró por inactividad."})

(def ^:private generic-error "No se pudo completar la acción.")

(defn- el [id] (.getElementById js/document id))

(defn status-text
  "T21: tipo de conexión y eco."
  [{:keys [open? type echo-ms]}]
  (if-not open?
    "Desconectado"
    (str "Conexión: " (if (= type :ws) "WebSocket" "Ajax (sin WebSocket)")
         (when echo-ms (str " · eco " echo-ms " ms")))))

(defn- show! [id visible?]
  (set! (.-hidden (el id)) (not visible?)))

(defn- text! [id s]
  (set! (.-textContent (el id)) (or s "")))

;; ---------------------------------------------------------------------------
;; Vistas

(defn- render-players! [players]
  (let [ul (el "room-players")]
    (set! (.-textContent ul) "")
    (doseq [{:keys [name join-order connected?]} players]
      (let [li (.createElement js/document "li")
            swatch (.createElement js/document "span")]
        (set! (.-className swatch) "swatch")
        (set! (.. swatch -style -backgroundColor) (nth config/player-colors join-order "#888888"))
        (.setAttribute swatch "aria-hidden" "true")
        (.appendChild li swatch)
        (.appendChild li (.createTextNode js/document (str name (when-not connected? " (desconectado)"))))
        (.appendChild ul li)))))

(defn- winner-text [{:keys [winner players]}]
  (if (= winner :draw)
    "Empate"
    (str "Ganó " (some #(when (= winner (:id %)) (:name %)) players))))

(defn- render! [{:keys [screen conn room error]}]
  (let [me (ws/my-uid)
        owner? (and room (= me (:owner room)))]
    (show! "lobby" (= screen :lobby))
    (show! "room" (= screen :room))
    (show! "game" (= screen :game))
    (text! "status" (status-text conn))
    (text! "error" error)
    (when room
      (text! "room-code" (:code room))
      (render-players! (:players room))
      (show! "start-game" (and owner?
                               (= :waiting (:status room))
                               (>= (count (:players room)) config/min-players-to-start)))
      (let [finished? (= :finished (:status room))]
        (show! "result" finished?)
        (when finished?
          (text! "result-text" (winner-text room)))
        (show! "restart" (and finished? owner?))))))

;; ---------------------------------------------------------------------------
;; Eventos del servidor

(defn- screen-for [status]
  (if (= status :waiting) :room :game))

(defn- on-push [[id data]]
  (case id
    :razti/room (swap! app assoc
                       :room data
                       :screen (screen-for (:status data))
                       :error nil)
    :razti/snapshot (swap! app (fn [s]
                                 (cond-> (assoc s :snapshot data)
                                   (:grid data) (assoc :grid (:grid data)))))
    :razti/error (let [reason (:reason data)]
                   (swap! app (fn [s]
                                (cond-> (assoc s :error (get error-messages reason generic-error))
                                  (= reason :room-closed) (assoc :screen :lobby :room nil
                                                                 :snapshot nil :grid nil)))))
    nil))

;; ---------------------------------------------------------------------------
;; Acciones

(defn- request! [ev on-ok]
  (ws/send! ev 5000
            (fn [reply]
              (cond
                (:ok reply) (on-ok (:ok reply))
                (:error reply) (swap! app assoc :error (get error-messages (:error reply) generic-error))
                :else (swap! app assoc :error generic-error)))))

(defn- input-value [id] (.trim (.-value (el id))))

(defn- valid-name? [s] (boolean (re-matches #"[A-Za-z0-9_-]{1,12}" s)))

(defn- create-room! []
  (let [n (input-value "name")]
    (if (valid-name? n)
      (request! [:razti/create-room {:name n}] (fn [_] nil))
      (swap! app assoc :error "El nombre debe tener de 1 a 12 letras, números, «_» o «-»."))))

(defn- join-room! []
  (let [n (input-value "name")
        code (.toUpperCase (input-value "code"))]
    (cond
      (not (valid-name? n))
      (swap! app assoc :error "El nombre debe tener de 1 a 12 letras, números, «_» o «-».")
      (not (re-matches #"[ABCDEFGHJKLMNPQRSTUVWXYZ2-9]{5}" code))
      (swap! app assoc :error "El código tiene 5 caracteres.")
      :else (request! [:razti/join-room {:code code :name n}] (fn [_] nil)))))

(defn- leave-room! []
  (request! [:razti/leave-room {}]
            (fn [_] (swap! app assoc :screen :lobby :room nil :snapshot nil :grid nil :error nil))))

(defn- sync! []
  (ws/send! [:razti/sync {}] 5000
            (fn [reply]
              (when (:ok reply)
                (if-let [room (get-in reply [:ok :room])]
                  (swap! app assoc :room room :screen (screen-for (:status room)))
                  ;; QA-06: el servidor ya no nos tiene en una sala → volver al lobby.
                  (when (not= :lobby (:screen @app))
                    (swap! app assoc :screen :lobby :room nil :snapshot nil :grid nil)))))))

(defn- on-click! [id f]
  (.addEventListener (el id) "click" (fn [e] (.preventDefault e) (f))))

(defn init []
  (on-click! "create-room" create-room!)
  (on-click! "join-room" join-room!)
  (on-click! "start-game" #(request! [:razti/start-game {}] (fn [_] nil)))
  (on-click! "leave-room" leave-room!)
  (on-click! "leave-game" leave-room!)
  (on-click! "restart" #(request! [:razti/restart {}] (fn [_] nil)))
  (add-watch app ::render (fn [_ _ _ s] (render! s)))
  (render! @app)
  (render/load-assets!)
  (render/start! app (el "game-canvas"))
  (input/install! app)
  (ws/connect! app on-push sync!))
