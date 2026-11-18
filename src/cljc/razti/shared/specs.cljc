(ns razti.shared.specs
  "Esquemas Malli. Se validan en la frontera de red (S02) y en las pruebas;
  nunca dentro de tick-game (C9)."
  (:require [malli.core :as m]
            [razti.shared.config :as config]))

;; Fin de texto estricto: `$` en la JVM también casa antes de un "\n" final
;; (Malli :re usa re-find). `(?![\s\S])` funciona igual en JVM y JS (SEC-01).
(def Nickname [:re #"^[A-Za-z0-9_-]{1,12}(?![\s\S])"])
(def RoomCode [:re #"^[ABCDEFGHJKLMNPQRSTUVWXYZ2-9]{5}(?![\s\S])"])

;; Límites (SEC-02): nada sin cota en lo que cruzará la frontera de red.
(def Coord [:int {:min 0 :max (dec config/grid-cols)}])
(def Pos [:tuple Coord Coord])
(def Dir [:enum :up :down :left :right])
(def PlayerId [:string {:min 1 :max 64}])
(def EntityId [:string {:min 1 :max 64}])
(def Seq [:int {:min 0 :max 2147483647}])
(def Counter [:int {:min 0 :max 2147483647}])
(def Ticks [:int {:min 0 :max 1000}])

(def Player
  [:map
   [:player/id PlayerId]
   [:player/name Nickname]
   [:player/pos [:maybe Pos]]
   [:player/dir Dir]
   [:player/alive? :boolean]
   [:player/cooldown Ticks]
   [:player/last-seq Seq]
   [:player/join-order Counter]
   [:player/connected? :boolean]])

(def Bomb
  [:map
   [:bomb/id EntityId]
   [:bomb/owner PlayerId]
   [:bomb/pos Pos]
   [:bomb/ticks-left Ticks]
   [:bomb/radius [:int {:min 1 :max config/grid-cols}]]
   [:bomb/pass-through [:set {:max config/max-players} PlayerId]]])

(def Blast
  [:map
   [:blast/id EntityId]
   [:blast/cells [:vector {:max (inc (* 4 config/grid-cols))} Pos]]
   [:blast/ticks-left Ticks]])

(def Grid [:vector {:min 1 :max config/grid-rows}
           [:vector {:min 1 :max config/grid-cols} [:enum 0 1 2]]])

(def Input
  [:multi {:dispatch :action}
   [:move [:map
           [:player-id PlayerId]
           [:action [:= :move]]
           [:dir Dir]
           [:seq {:optional true} Seq]]]
   [:bomb [:map
           [:player-id PlayerId]
           [:action [:= :bomb]]
           [:seq {:optional true} Seq]]]
   [:leave [:map
            [:player-id PlayerId]
            [:action [:= :leave]]]]])

(def Room
  [:map
   [:room/code RoomCode]
   [:room/status [:enum :waiting :playing :finished]]
   [:room/owner [:maybe PlayerId]]
   [:room/tick Counter]
   [:room/next-id Counter]
   [:room/grid Grid]
   [:room/initial-grid Grid]
   [:room/spawns [:vector {:max config/max-players} Pos]]
   [:room/grid-version Counter]
   [:room/inputs [:vector {:max 256} Input]]
   [:room/players [:map-of {:max config/max-players} PlayerId Player]]
   [:room/bombs [:vector {:max 64} Bomb]]
   [:room/blasts [:vector {:max 64} Blast]]
   [:room/winner [:maybe [:or PlayerId [:= :draw]]]]])

;; Eventos del cliente (frontera, S02). El servidor añade :player-id desde la sesión.
(def ClientEvent
  [:multi {:dispatch first}
   [:razti/create-room [:tuple [:= :razti/create-room] [:map [:name Nickname]]]]
   [:razti/join-room [:tuple [:= :razti/join-room] [:map [:code RoomCode] [:name Nickname]]]]
   [:razti/start-game [:tuple [:= :razti/start-game] [:map]]]
   [:razti/restart [:tuple [:= :razti/restart] [:map]]]
   [:razti/input [:tuple [:= :razti/input] [:map
                                            [:seq Seq]
                                            [:action [:enum :move :bomb]]
                                            [:dir {:optional true} Dir]]]]])

(def ^:private room-validator (m/validator Room))
(def ^:private input-validator (m/validator Input))
(def ^:private client-event-validator (m/validator ClientEvent))

(defn valid-room? [room] (room-validator room))
(defn valid-input? [input] (input-validator input))
(defn valid-client-event? [event] (client-event-validator event))
(defn explain-room [room] (m/explain Room room))
