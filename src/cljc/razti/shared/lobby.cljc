(ns razti.shared.lobby
  "Reglas puras del lobby (brief §5, C6; TDD M1/S01 §4). Salvo `new-room`,
  cada función devuelve {:room room'} o {:error <kw>}. El código de sala lo
  genera el servidor (único punto con aleatoriedad)."
  (:require [razti.shared.config :as config]
            [razti.shared.engine :as engine]))

(defn- new-player [id player-name join-order]
  {:player/id id
   :player/name player-name
   :player/pos nil
   :player/dir :down
   :player/alive? true
   :player/cooldown 0
   :player/last-seq 0
   :player/join-order join-order
   :player/connected? true})

(defn new-room
  "Sala en :waiting con su creador como dueño."
  [{:keys [code player-id grid spawns] player-name :name}]
  {:room/code code
   :room/status :waiting
   :room/owner player-id
   :room/tick 0
   :room/next-id 1
   :room/grid grid
   :room/initial-grid grid
   :room/spawns spawns
   :room/grid-version 0
   :room/inputs []
   :room/players {player-id (new-player player-id player-name 0)}
   :room/bombs []
   :room/blasts []
   :room/winner nil})

(defn empty-room?
  "true si no queda ningún jugador conectado (el servidor elimina la sala)."
  [room]
  (not-any? :player/connected? (vals (:room/players room))))

(defn join-room
  [room {:keys [code player-id] player-name :name}]
  (let [players (:room/players room)]
    (cond
      (not= code (:room/code room)) {:error :room-not-found}
      (not= :waiting (:room/status room)) {:error :game-in-progress}
      (contains? players player-id) {:error :already-joined}
      (>= (count players) config/max-players) {:error :room-full}
      (some #(= player-name (:player/name %)) (vals players)) {:error :name-taken}
      :else (let [n (:room/next-id room)]
              {:room (-> room
                         (assoc-in [:room/players player-id] (new-player player-id player-name n))
                         (assoc :room/next-id (inc n)))}))))

(defn- next-owner
  "D6a: el jugador conectado con menor :player/join-order, o nil."
  [room]
  (->> (vals (:room/players room))
       (filter :player/connected?)
       (sort-by :player/join-order)
       first
       :player/id))

(defn leave-room
  "En :playing marca al jugador como desconectado y encola :leave (R11, T7);
  en otro estado lo quita. Si era el dueño, el dueño pasa al siguiente (D6a)."
  [room player-id]
  (if-not (contains? (:room/players room) player-id)
    {:room room}
    (let [room (if (= :playing (:room/status room))
                 (-> room
                     (assoc-in [:room/players player-id :player/connected?] false)
                     (engine/enqueue-input {:player-id player-id :action :leave}))
                 (update room :room/players dissoc player-id))
          room (if (= player-id (:room/owner room))
                 (assoc room :room/owner (next-owner room))
                 room)]
      {:room room})))

(defn- begin-game
  "Partida nueva con la grilla inicial y las apariciones por orden de llegada (T10)."
  [room]
  (let [spawns (:room/spawns room)
        players (->> (vals (:room/players room))
                     (sort-by :player/join-order)
                     (map-indexed (fn [i p]
                                    [(:player/id p)
                                     (assoc p
                                            :player/pos (nth spawns i)
                                            :player/alive? true
                                            :player/cooldown 0
                                            :player/dir :down
                                            :player/last-seq 0)]))
                     (into {}))]
    (-> room
        (assoc :room/status :playing
               :room/grid (:room/initial-grid room)
               :room/bombs []
               :room/blasts []
               :room/inputs []
               :room/tick 0
               :room/winner nil
               :room/players players)
        (update :room/grid-version inc))))

(defn start-game [room player-id]
  (cond
    (not= player-id (:room/owner room)) {:error :not-owner}
    (not= :waiting (:room/status room)) {:error :invalid-status}
    (< (count (:room/players room)) config/min-players-to-start) {:error :not-enough-players}
    :else {:room (begin-game room)}))

(defn restart
  "D6b, T8: solo el dueño, solo en :finished; quita a los desconectados y
  empieza una partida nueva."
  [room player-id]
  (cond
    (not= player-id (:room/owner room)) {:error :not-owner}
    (not= :finished (:room/status room)) {:error :invalid-status}
    :else (let [room (update room :room/players
                             #(into {} (filter (comp :player/connected? val)) %))]
            (if (< (count (:room/players room)) config/min-players-to-start)
              {:error :not-enough-players}
              {:room (begin-game room)}))))
