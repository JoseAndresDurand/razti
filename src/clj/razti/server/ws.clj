(ns razti.server.ws
  "Sente (WebSocket), router de eventos y límite de entradas (TDD M2/S02 §4;
  núcleo T11, T12, T16, T17, T18)."
  (:require [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as transit]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [razti.shared.config :as config]
            [razti.shared.engine :as engine]
            [razti.shared.specs :as specs]
            [razti.server.log :refer [log]]
            [razti.server.loop :as loop]
            [razti.server.rooms :as rooms]))

(set! *warn-on-reflection* true)

(defn- describe-rejection
  "Datos de diagnóstico de un rechazo, sin tokens ni cookies."
  [req]
  (let [h (:headers req)]
    (str (name (or (:request-method req) :?)) " " (:uri req)
         " origin=" (pr-str (get h "origin"))
         " referer?=" (contains? h "referer")
         " host=" (pr-str (get h "host"))
         " x-forwarded-host=" (pr-str (get h "x-forwarded-host"))
         " sesión?=" (boolean (get-in req [:session :csrf-token]))
         " token-cliente?=" (boolean (or (get-in req [:params :csrf-token])
                                         (get h "x-csrf-token"))))))

(defn make-sente
  "Canal de Sente con Transit, orígenes permitidos y CSRF por defecto
  (lee [:session :csrf-token], T11; uid desde [:session :uid], T12).
  Cada rechazo se registra con su motivo (diagnóstico de proxies)."
  [cfg]
  (sente/make-channel-socket-server!
   (get-sch-adapter)
   {:packer (transit/get-packer)
    :allowed-origins (:allowed-origins cfg)
    :bad-origin-fn (fn [req]
                     (log :warn "rechazo por origen:" (describe-rejection req))
                     {:status 403 :body "Unauthorized origin"})
    :bad-csrf-fn (fn [req]
                   (log :warn "rechazo por CSRF:" (describe-rejection req))
                   {:status 403 :body "Bad CSRF token"})}))

;; ---------------------------------------------------------------------------
;; Límite de entradas (T16): ventana deslizante de 1 s por uid

(defn allow-event?
  "true si el uid no superó config/max-inputs-per-second y el servidor no superó
  config/max-events-per-second-global en el último segundo (T16, SEC-03);
  registra el evento cuando lo permite."
  [limiter uid now-ms]
  (let [ok (volatile! false)
        window #(filterv (fn [t] (> t (- now-ms 1000))) %)]
    (swap! limiter
           (fn [m]
             (let [recent (window (get m uid []))
                   global (window (get m ::global []))]
               (if (and (< (count recent) config/max-inputs-per-second)
                        (< (count global) config/max-events-per-second-global))
                 (do (vreset! ok true)
                     (assoc m uid (conj recent now-ms) ::global (conj global now-ms)))
                 (do (vreset! ok false)
                     (assoc m uid recent ::global global))))))
    @ok))

(defn purge-limiter!
  "Quita del limiter los uids sin eventos en el último segundo (SEC-02)."
  [limiter now-ms]
  (swap! limiter (fn [m]
                   (into {} (filter (fn [[_ ts]] (some #(> % (- now-ms 1000)) ts))) m))))

;; ---------------------------------------------------------------------------
;; Router

(defn- reply! [?reply-fn value]
  (when ?reply-fn (?reply-fn value)))

(defn- broadcast-room! [system code]
  (when-let [ra (rooms/room-atom (:rooms system) code)]
    (let [room @ra]
      (rooms/send-room! system room [:razti/room (rooms/view room)]))))

(defn- as-reply [res]
  (if (:ok res) {:ok {:code (:ok res)}} res))

(defn- on-leave! [system uid now]
  (let [res (rooms/leave! (:rooms system) uid now)]
    (when (and (:ok res) (not (:removed? res)))
      (broadcast-room! system (:ok res)))
    res))

(defn handle-event!
  "Router de eventos. Los :chsk/* internos van primero; el resto se valida
  con Malli y con el límite de entradas antes de tocar el estado."
  [system {:keys [id ?data ?reply-fn event uid]}]
  (let [st (:rooms system)
        now ((:now-ms system))]
    (cond
      (= :chsk/uidport-close id) (do (swap! (:limiter system) dissoc uid)
                                     (on-leave! system uid now))

      (= "chsk" (namespace id)) nil

      ;; El límite va antes de validar: los inválidos también cuentan (SEC-02).
      (not (allow-event? (:limiter system) uid now)) nil

      (not (specs/valid-client-event? event))
      (log :warn "evento inválido descartado" (pr-str id) "uid" (pr-str uid))

      :else
      (do
        (when-let [code (rooms/code-of st uid)]
          (rooms/touch! st code now))
        (case id
          :razti/echo (reply! ?reply-fn ?data)

          ;; Al (re)conectar: si el uid sigue en una sala (recarga, otra pestaña),
          ;; el cliente recupera su vista en lugar de quedar atrapado en el lobby.
          :razti/sync
          (reply! ?reply-fn {:ok {:room (some-> (rooms/room-atom-of st uid) deref rooms/view)}})

          :razti/create-room
          (let [res (rooms/create! st uid (:name ?data) now)]
            (reply! ?reply-fn (as-reply res))
            (when (:ok res) (broadcast-room! system (:ok res))))

          :razti/join-room
          (let [res (rooms/join! st uid (:code ?data) (:name ?data) now)]
            (reply! ?reply-fn (as-reply res))
            (when (:ok res) (broadcast-room! system (:ok res))))

          :razti/leave-room
          (let [res (on-leave! system uid now)]
            (reply! ?reply-fn (if (:ok res) {:ok {}} res)))

          (:razti/start-game :razti/restart)
          (let [f (if (= id :razti/start-game) rooms/start! rooms/restart!)
                res (f st uid now)]
            (reply! ?reply-fn (if (:ok res) {:ok {}} res))
            (when (:ok res)
              (loop/start-clock! system (:ok res))
              (broadcast-room! system (:ok res))))

          :razti/input
          (when-let [ra (rooms/room-atom-of st uid)]
            ;; El :player-id sale siempre de la sesión (T12), nunca del cliente.
            (swap! ra engine/enqueue-input (assoc ?data :player-id uid)))

          (log :warn "evento sin manejador" (pr-str id)))))))

(defn sweep!
  "Barrido (T15, SEC-01, SEC-02): salas inactivas (avisa :room-closed),
  ventanas vacías del limiter y sesiones caducadas."
  [system]
  (let [send-fn (get-in system [:sente :send-fn])
        now ((:now-ms system))]
    (purge-limiter! (:limiter system) now)
    (when-let [purge (:purge-sessions! system)] (purge now))
    (doseq [[code uids] (rooms/sweep! (:rooms system) now)]
      (log :info "sala eliminada:" code)
      (doseq [uid uids]
        (send-fn uid [:razti/error {:reason :room-closed}])))))

(defn start-router!
  "Arranca el router de Sente. Devuelve la función de parada."
  [system]
  (sente/start-server-chsk-router!
   (get-in system [:sente :ch-recv])
   (fn [ev-msg]
     (try
       (handle-event! system ev-msg)
       (catch Throwable t
         (log :error "fallo en el router:" (.getMessage t)))))))
