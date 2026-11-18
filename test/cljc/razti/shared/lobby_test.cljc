(ns razti.shared.lobby-test
  (:require [clojure.test :refer [deftest is testing]]
            [razti.shared.fixtures :as f]
            [razti.shared.lobby :as lobby]))

(def ^:private g (f/mini-grid 7 7))
(def ^:private spawns [[1 1] [5 1] [1 5] [5 5]])

(defn- room-with
  "Sala `code` en :waiting con los jugadores p1…pn (p1 es el dueño)."
  [code n]
  (reduce (fn [room i]
            (:room (lobby/join-room room {:code code
                                          :player-id (str "p" i)
                                          :name (str "P" i)})))
          (lobby/new-room {:code code :player-id "p1" :name "P1" :grid g :spawns spawns})
          (range 2 (inc n))))

(defn- player-ids [room] (set (keys (:room/players room))))

;; ---------------------------------------------------------------------------
;; Escenario BDD del brief §8

(deftest ^:S01 escenario-3-sala-llena
  (testing "3. Sala llena"
    (let [room (room-with "AQP26" 4)
          result (lobby/join-room room {:code "AQP26" :player-id "p5" :name "P5"})]
      (is (= 4 (count (:room/players room))))
      (is (= {:error :room-full} result))
      (is (= 4 (count (:room/players room)))))))

;; ---------------------------------------------------------------------------
;; Reglas decididas (TDD §1.5)

(deftest ^:S01 join-room-errores
  (testing "join-room: código, nombre repetido (D6c) y partida en curso"
    (let [room (room-with "UNSA2" 2)]
      (is (= {:error :room-not-found} (lobby/join-room room {:code "ZZZZ2" :player-id "p9" :name "X"})))
      (is (= {:error :name-taken} (lobby/join-room room {:code "UNSA2" :player-id "p9" :name "P1"})))
      (is (= {:error :already-joined} (lobby/join-room room {:code "UNSA2" :player-id "p2" :name "Otro"})))
      (is (= {:error :game-in-progress}
             (lobby/join-room (:room (lobby/start-game room "p1"))
                              {:code "UNSA2" :player-id "p9" :name "X"}))))))

(deftest ^:S01 start-game-reglas
  (testing "start-game: solo el dueño, con 2 o más jugadores; apariciones por orden de llegada (T10)"
    (let [solo (room-with "UNSA2" 1)
          room (room-with "UNSA2" 3)
          started (:room (lobby/start-game room "p1"))]
      (is (= {:error :not-enough-players} (lobby/start-game solo "p1")))
      (is (= {:error :not-owner} (lobby/start-game room "p2")))
      (is (= :playing (:room/status started)))
      (is (= [1 1] (get-in started [:room/players "p1" :player/pos])))
      (is (= [5 1] (get-in started [:room/players "p2" :player/pos])))
      (is (= [1 5] (get-in started [:room/players "p3" :player/pos])))
      (is (= {:error :invalid-status} (lobby/start-game started "p1"))))))

(deftest ^:S01 d6a-traspaso-de-dueno
  (testing "D6a: si el dueño sale, el dueño pasa al siguiente por orden de llegada"
    (let [room (room-with "UNSA2" 3)
          after (:room (lobby/leave-room room "p1"))]
      (is (= #{"p2" "p3"} (player-ids after)))
      (is (= "p2" (:room/owner after)))
      (is (not (lobby/empty-room? after))))
    (let [room (room-with "UNSA2" 2)
          after (-> room
                    (lobby/leave-room "p1") :room
                    (lobby/leave-room "p2") :room)]
      (is (lobby/empty-room? after))
      (is (nil? (:room/owner after))))))

(deftest ^:S01 d6a-salida-en-partida
  (testing "R11/T7: salir en :playing desconecta, encola :leave y traspasa el dueño"
    (let [started (:room (lobby/start-game (room-with "UNSA2" 3) "p1"))
          after (:room (lobby/leave-room started "p1"))]
      (is (= #{"p1" "p2" "p3"} (player-ids after)))
      (is (false? (get-in after [:room/players "p1" :player/connected?])))
      (is (= [{:player-id "p1" :action :leave}] (:room/inputs after)))
      (is (= "p2" (:room/owner after))))))

(deftest ^:S01 d6b-reinicio
  (testing "D6b/T8: solo el dueño reinicia, en :finished, sin los desconectados"
    (let [started (:room (lobby/start-game (room-with "UNSA2" 3) "p1"))
          finished (-> started
                       (assoc-in [:room/players "p3" :player/connected?] false)
                       (assoc :room/status :finished :room/winner "p1"))
          restarted (:room (lobby/restart finished "p1"))]
      (is (= {:error :invalid-status} (lobby/restart started "p1")))
      (is (= {:error :not-owner} (lobby/restart finished "p2")))
      (is (= :playing (:room/status restarted)))
      (is (= #{"p1" "p2"} (player-ids restarted)))
      (is (nil? (:room/winner restarted)))
      (is (= [5 1] (get-in restarted [:room/players "p2" :player/pos]))))
    (let [started (:room (lobby/start-game (room-with "UNSA2" 2) "p1"))
          finished (-> started
                       (assoc-in [:room/players "p2" :player/connected?] false)
                       (assoc :room/status :finished :room/winner "p1"))]
      (is (= {:error :not-enough-players} (lobby/restart finished "p1"))))))

(deftest ^:S01 join-room-caso-feliz
  (testing "QA-01: el creador tiene join-order 0 y cada invitado el siguiente contador"
    (let [room (room-with "UNSA2" 3)]
      (is (= 0 (get-in room [:room/players "p1" :player/join-order])))
      (is (= 1 (get-in room [:room/players "p2" :player/join-order])))
      (is (= 2 (get-in room [:room/players "p3" :player/join-order])))
      (is (= 3 (:room/next-id room)))
      (is (= {:player/connected? true :player/alive? true :player/pos nil}
             (select-keys (get-in room [:room/players "p3"])
                          [:player/connected? :player/alive? :player/pos]))))))

(deftest ^:S01 join-room-precedencia
  (testing "QA-03: orden de errores not-found > in-progress > already-joined > full > name-taken"
    (let [full (room-with "UNSA2" 4)
          started (:room (lobby/start-game full "p1"))]
      (is (= {:error :room-not-found}
             (lobby/join-room started {:code "ZZZZ2" :player-id "p1" :name "P1"})))
      (is (= {:error :game-in-progress}
             (lobby/join-room started {:code "UNSA2" :player-id "p1" :name "P1"})))
      (is (= {:error :already-joined}
             (lobby/join-room full {:code "UNSA2" :player-id "p2" :name "P2"})))
      (is (= {:error :room-full}
             (lobby/join-room full {:code "UNSA2" :player-id "p9" :name "P1"}))))))

(deftest ^:S01 begin-game-restaura-el-estado
  (testing "QA-04 (T8, T10): restart vuelve a la grilla inicial y reinicia jugadores, bombas y blasts"
    (let [started (:room (lobby/start-game (room-with "UNSA2" 2) "p1"))
          gv (:room/grid-version started)
          finished (-> started
                       (assoc-in [:room/grid 1 1] 2)
                       (update :room/grid-version inc)
                       (assoc :room/bombs [{:bomb/id "b-9" :bomb/owner "p2" :bomb/pos [3 3]
                                            :bomb/ticks-left 10 :bomb/radius 2
                                            :bomb/pass-through #{}}])
                       (assoc :room/blasts [{:blast/id "bl-9" :blast/cells [[3 3]]
                                             :blast/ticks-left 3}])
                       (assoc :room/inputs [{:player-id "p1" :action :bomb}])
                       (assoc-in [:room/players "p2" :player/alive?] false)
                       (assoc-in [:room/players "p1" :player/cooldown] 3)
                       (assoc-in [:room/players "p1" :player/dir] :left)
                       (assoc :room/tick 120 :room/status :finished :room/winner "p1"))
          r (:room (lobby/restart finished "p1"))]
      (is (= 1 gv))
      (is (= (:room/initial-grid r) (:room/grid r)))
      (is (= (inc (:room/grid-version finished)) (:room/grid-version r)))
      (is (= [] (:room/bombs r) (:room/blasts r) (:room/inputs r)))
      (is (= 0 (:room/tick r)))
      (is (true? (get-in r [:room/players "p2" :player/alive?])))
      (is (= 0 (get-in r [:room/players "p1" :player/cooldown])))
      (is (= :down (get-in r [:room/players "p1" :player/dir])))
      (is (= [1 1] (get-in r [:room/players "p1" :player/pos]))))))
