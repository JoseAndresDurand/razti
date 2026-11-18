(ns razti.server.rooms
  "Registro de salas: un atom para el lobby y un atom por sala (TDD M2/S02 §4;
  núcleo T15, T17). El reloj (now-ms) se inyecta para las pruebas."
  (:require [razti.shared.campus :as campus]
            [razti.shared.config :as config]
            [razti.shared.lobby :as lobby])
  (:import (java.security SecureRandom)
           (java.util.concurrent Future)))

(set! *warn-on-reflection* true)

(defn new-state
  "{:rooms {code {:room (atom room) :last-activity-ms n :clock Future}} :uid->code {uid code}}"
  []
  (atom {:rooms {} :uid->code {}}))

(defonce ^:private ^SecureRandom rng (SecureRandom.))

(defn new-code
  "Código de 5 caracteres del alfabeto sin 0/O/1/I; reintenta si choca con `taken`."
  [taken]
  (let [alphabet config/room-code-alphabet
        n (count alphabet)]
    (loop []
      (let [code (apply str (repeatedly config/room-code-length
                                        #(nth alphabet (.nextInt rng (int n)))))]
        (if (contains? taken code) (recur) code)))))

;; ---------------------------------------------------------------------------
;; Consultas

(defn code-of [state uid] (get-in @state [:uid->code uid]))

(defn room-atom [state code] (get-in @state [:rooms code :room]))

(defn room-atom-of [state uid]
  (some->> (code-of state uid) (room-atom state)))

(defn clock [state code] (get-in @state [:rooms code :clock]))

(defn member-uids
  "Jugadores conectados de la sala (destinatarios de los envíos)."
  [room]
  (->> (vals (:room/players room))
       (filter :player/connected?)
       (map :player/id)))

(defn view
  "Vista de sala para el lobby y el resultado."
  [room]
  {:code (:room/code room)
   :status (:room/status room)
   :owner (:room/owner room)
   :winner (:room/winner room)
   :players (->> (vals (:room/players room))
                 (sort-by :player/join-order)
                 (mapv (fn [p] {:id (:player/id p)
                                :name (:player/name p)
                                :join-order (:player/join-order p)
                                :connected? (:player/connected? p)
                                :alive? (:player/alive? p)})))})

(defn send-room!
  "Envía `ev` a cada jugador conectado de la sala por Sente."
  [system room ev]
  (let [send-fn (get-in system [:sente :send-fn])]
    (doseq [uid (member-uids room)]
      (send-fn uid ev))))

;; ---------------------------------------------------------------------------
;; Transiciones

(defn- apply-lobby!
  "Aplica (f room) → {:room r'} | {:error kw} dentro de swap!; devuelve el resultado."
  [room-atom f]
  (let [result (volatile! nil)]
    (swap! room-atom (fn [r]
                       (let [res (f r)]
                         (vreset! result res)
                         (or (:room res) r))))
    @result))

(defn touch!
  "Marca actividad de la sala (T15)."
  [state code now-ms]
  (swap! state (fn [st]
                 (if (contains? (:rooms st) code)
                   (assoc-in st [:rooms code :last-activity-ms] now-ms)
                   st))))

(defn set-clock!
  "Registra el reloj de la sala; si ya había uno, cancela el nuevo. → true si quedó."
  [state code ^Future f]
  (let [ok (volatile! false)]
    (swap! state (fn [st]
                   (vreset! ok false)
                   (if (and (contains? (:rooms st) code)
                            (nil? (get-in st [:rooms code :clock])))
                     (do (vreset! ok true)
                         (assoc-in st [:rooms code :clock] f))
                     st)))
    (when-not @ok (.cancel f false))
    @ok))

(defn clear-clock-if!
  "Cancela `f` y, si es el reloj registrado de la sala, lo quita (QA-01, QA-02:
  una tarea solo puede retirarse a sí misma, nunca al reloj de un reinicio)."
  [state code ^Future f]
  (.cancel f false)
  (swap! state (fn [st]
                 (if (identical? f (get-in st [:rooms code :clock]))
                   (assoc-in st [:rooms code :clock] nil)
                   st))))

(defn remove-room!
  "Elimina la sala y cancela el reloj que tenía al quitarla (QA-02).
  → uids conectados que tenía (para avisarles)."
  [state code]
  (let [ra (room-atom state code)
        uids (if ra (vec (member-uids @ra)) [])
        [old _] (swap-vals! state
                            (fn [st]
                              (-> st
                                  (update :rooms dissoc code)
                                  (update :uid->code #(into {} (remove (fn [[_ c]] (= c code))) %)))))]
    (when-let [^Future f (get-in old [:rooms code :clock])]
      (.cancel f false))
    uids))

(defn create!
  "→ {:ok code} | {:error :already-in-room | :server-full}"
  [state uid player-name now-ms]
  (let [result (volatile! nil)]
    (swap! state
           (fn [{:keys [rooms uid->code] :as st}]
             (cond
               (contains? uid->code uid) (do (vreset! result {:error :already-in-room}) st)
               (>= (count rooms) config/max-rooms) (do (vreset! result {:error :server-full}) st)
               :else
               (let [code (new-code (set (keys rooms)))
                     room (lobby/new-room {:code code :player-id uid :name player-name
                                           :grid campus/INITIAL-GRID :spawns campus/SPAWNS})]
                 (vreset! result {:ok code})
                 (-> st
                     (assoc-in [:rooms code] {:room (atom room) :last-activity-ms now-ms :clock nil})
                     (assoc-in [:uid->code uid] code))))))
    @result))

(defn join!
  "→ {:ok code} | {:error kw}"
  [state uid code player-name now-ms]
  (cond
    (code-of state uid) {:error :already-in-room}
    (nil? (room-atom state code)) {:error :room-not-found}
    :else
    (let [res (apply-lobby! (room-atom state code)
                            #(lobby/join-room % {:code code :player-id uid :name player-name}))]
      (if (:room res)
        ;; QA-03: si la sala se eliminó durante el join, no hay sala a la que unirse.
        (let [registered (volatile! false)]
          (swap! state (fn [st]
                         (vreset! registered (contains? (:rooms st) code))
                         (if @registered
                           (assoc-in st [:uid->code uid] code)
                           st)))
          (if @registered
            (do (touch! state code now-ms) {:ok code})
            {:error :room-not-found}))
        res))))

(defn leave!
  "Sale de la sala (R11/T7 en :playing). Si no queda nadie conectado, la elimina.
  → {:ok code :removed? bool} | {:error :not-in-room}"
  [state uid now-ms]
  (if-let [code (code-of state uid)]
    (let [ra (room-atom state code)]
      (when ra (apply-lobby! ra #(lobby/leave-room % uid)))
      (swap! state update :uid->code dissoc uid)
      (touch! state code now-ms)
      (let [removed? (boolean (and ra (lobby/empty-room? @ra)))]
        (when removed? (remove-room! state code))
        {:ok code :removed? removed?}))
    {:error :not-in-room}))

(defn- owner-action! [state uid f now-ms]
  (if-let [code (code-of state uid)]
    (let [res (apply-lobby! (room-atom state code) #(f % uid))]
      (touch! state code now-ms)
      (if (:room res) {:ok code} res))
    {:error :not-in-room}))

(defn start! [state uid now-ms] (owner-action! state uid lobby/start-game now-ms))

(defn restart! [state uid now-ms] (owner-action! state uid lobby/restart now-ms))

(defn sweep!
  "T15: elimina salas sin conectados o con room-idle-ms sin actividad.
  → [[code uids-a-avisar] …]"
  [state now-ms]
  (let [doomed (for [[code {:keys [room last-activity-ms]}] (:rooms @state)
                     :when (or (lobby/empty-room? @room)
                               (>= (- now-ms last-activity-ms) config/room-idle-ms))]
                 code)]
    (mapv (fn [code] [code (remove-room! state code)]) doomed)))
