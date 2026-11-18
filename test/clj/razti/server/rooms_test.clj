(ns razti.server.rooms-test
  (:require [clojure.test :refer [deftest is testing]]
            [razti.shared.config :as config]
            [razti.shared.engine :as engine]
            [razti.shared.lobby :as lobby]
            [razti.server.loop :as loop]
            [razti.server.rooms :as rooms]
            [razti.server.ws :as ws]))

(def ^:private t0 1000000)

(deftest ^:S02 codigos-de-sala
  (testing "5 caracteres del alfabeto sin 0/O/1/I"
    (dotimes [_ 200]
      (is (re-matches #"[ABCDEFGHJKLMNPQRSTUVWXYZ2-9]{5}" (rooms/new-code #{}))))))

(deftest ^:S02 escenario-S02-5-limites
  (let [st (rooms/new-state)
        [c1 c2 c3] (map #(:ok (rooms/create! st % (str "J" %) t0)) ["u1" "u2" "u3"])]
    (testing "S02-5. Con 3 salas activas, una cuarta → :server-full"
      (is (every? some? [c1 c2 c3]))
      (is (= {:error :server-full} (rooms/create! st "u4" "J4" t0))))
    (testing "T17. Una sala por uid"
      (is (= {:error :already-in-room} (rooms/create! st "u1" "J1" t0)))
      (is (= {:error :already-in-room} (rooms/join! st "u1" c2 "J1" t0))))
    (testing "Unirse a un código inexistente"
      (is (= {:error :room-not-found} (rooms/join! st "u9" "ZZZZ2" "J9" t0))))
    (testing "S02-5. Inactividad: no a los 299 999 ms, sí a los 300 000 ms"
      (rooms/touch! st c2 (+ t0 1))
      (is (= [] (rooms/sweep! st (+ t0 (dec config/room-idle-ms)))))
      (let [removed (rooms/sweep! st (+ t0 config/room-idle-ms))]
        (is (= #{c1 c3} (set (map first removed))))
        (is (= #{["u1"] ["u3"]} (set (map second removed)))))
      (is (nil? (rooms/code-of st "u1")))
      (is (some? (rooms/room-atom st c2))))))

(deftest ^:S02 salida-y-salas-vacias
  (let [st (rooms/new-state)
        code (:ok (rooms/create! st "u1" "J1" t0))]
    (rooms/join! st "u2" code "J2" t0)
    (testing "Si sale el dueño, la sala sigue con nuevo dueño (D6a)"
      (is (= {:ok code :removed? false} (rooms/leave! st "u1" t0)))
      (is (= "u2" (:room/owner @(rooms/room-atom st code)))))
    (testing "Si sale el último, la sala se elimina"
      (is (= {:ok code :removed? true} (rooms/leave! st "u2" t0)))
      (is (nil? (rooms/room-atom st code))))
    (testing "Barrido de una sala sin jugadores conectados"
      (let [c (:ok (rooms/create! st "u3" "J3" t0))]
        (swap! (rooms/room-atom st c) assoc-in [:room/players "u3" :player/connected?] false)
        (is (= [[c []]] (rooms/sweep! st t0)))))))

(deftest ^:S02 snapshot-sin-grilla
  (let [st (rooms/new-state)
        code (:ok (rooms/create! st "u1" "J1" t0))
        _ (rooms/join! st "u2" code "J2" t0)
        _ (rooms/start! st "u1" t0)
        room @(rooms/room-atom st code)
        gv (:room/grid-version room)]
    (testing "T14. Con la misma versión de grilla, el snapshot no la incluye"
      (is (not (contains? (loop/snapshot room gv) :grid))))
    (testing "T14. Si la versión cambió, la incluye"
      (is (= (:room/grid room) (:grid (loop/snapshot room (dec gv))))))
    (testing "Forma del snapshot"
      (let [s (loop/snapshot room gv)]
        (is (= #{:tick :status :winner :grid-version :players :bombs :blasts} (set (keys s))))
        (is (= #{:name :pos :dir :alive? :join-order :last-seq} (set (keys (get-in s [:players "u1"])))))))))

;; ---------------------------------------------------------------------------
;; Router con un send-fn falso (sin red ni reloj)

(defn- fake-system []
  (let [sent (atom [])]
    {:rooms (rooms/new-state)
     :limiter (atom {})
     :now-ms (constantly t0)
     :sente {:send-fn (fn [uid ev] (swap! sent conj [uid ev]))}
     :sent sent}))

(defn- dispatch!
  "Simula un evento de Sente; devuelve la respuesta del callback o :none."
  [system uid event]
  (let [reply (promise)]
    (ws/handle-event! system {:id (if (vector? event) (first event) :razti/bad)
                              :?data (when (vector? event) (second event))
                              :event event
                              :uid uid
                              :?reply-fn #(deliver reply %)})
    (deref reply 0 :none)))

(deftest ^:S02 router
  (let [sys (fake-system)
        st (:rooms sys)]
    (testing "Eventos inválidos: sin efecto y sin excepción"
      (is (= :none (dispatch! sys "u1" [:razti/create-room {:name "Ana" :player-id "u9"}])))
      (is (= :none (dispatch! sys "u1" 5)))
      (is (empty? (:rooms @st))))
    (let [{{:keys [code]} :ok} (dispatch! sys "u1" [:razti/create-room {:name "Ana"}])]
      (testing "Crear, unirse e iniciar responden {:ok …} y difunden la vista"
        (is (string? code))
        (is (= {:ok {:code code}} (dispatch! sys "u2" [:razti/join-room {:code code :name "Beto"}])))
        (is (= {:error :not-owner} (dispatch! sys "u2" [:razti/start-game {}])))
        (is (= {:ok {}} (dispatch! sys "u1" [:razti/start-game {}])))
        (is (some (fn [[uid [id v]]] (and (= uid "u2") (= id :razti/room) (= :playing (:status v))))
                  @(:sent sys))))
      (testing "T12. :razti/input usa el uid de la sesión"
        (dispatch! sys "u1" [:razti/input {:seq 1 :action :bomb}])
        (is (= [{:seq 1 :action :bomb :player-id "u1"}]
               (:room/inputs @(rooms/room-atom st code))))
        (is (= :none (dispatch! sys "u1" [:razti/input {:seq 2 :action :bomb :player-id "u2"}])))
        (is (= 1 (count (:room/inputs @(rooms/room-atom st code))))))
      (testing "R11/T18. :chsk/uidport-close en :playing elimina al jugador en el siguiente tick"
        (dispatch! sys "u2" [:chsk/uidport-close nil])
        (let [room (swap! (rooms/room-atom st code) engine/tick-game)]
          (is (false? (get-in room [:room/players "u2" :player/alive?])))
          (is (= :finished (:room/status room)))
          (is (= "u1" (:room/winner room))))))))

(deftest ^:S02 sincronizacion-al-reconectar
  (let [sys (fake-system)]
    (testing "Sin sala: {:ok {:room nil}}"
      (is (= {:ok {:room nil}} (dispatch! sys "u1" [:razti/sync {}]))))
    (testing "Con sala (recarga u otra pestaña de la misma sesión): devuelve su vista"
      (let [{{:keys [code]} :ok} (dispatch! sys "u1" [:razti/create-room {:name "Ana"}])
            room (get-in (dispatch! sys "u1" [:razti/sync {}]) [:ok :room])]
        (is (= code (:code room)))
        (is (= :waiting (:status room)))
        (is (= "u1" (:owner room)))))))

(defn- await-true [pred ms]
  (let [deadline (+ (System/currentTimeMillis) ms)]
    (loop [] (or (pred) (when (< (System/currentTimeMillis) deadline) (Thread/sleep 20) (recur))))))

(defn- clock-system []
  (assoc (fake-system) :executor (loop/new-executor) :now-ms #(System/currentTimeMillis)))

(defn- playing-room! [sys]
  (let [st (:rooms sys)
        code (:ok (rooms/create! st "u1" "Ana" t0))]
    (rooms/join! st "u2" code "Beto" t0)
    (rooms/start! st "u1" t0)
    code))

(deftest ^:S02 relojes-sin-carreras
  (testing "QA-01. Reiniciar justo al terminar no deja la sala sin reloj"
    (let [sys (clock-system) st (:rooms sys) code (playing-room! sys)]
      (try
        (loop/start-clock! sys code)
        (swap! (rooms/room-atom st code) assoc :room/status :finished :room/winner "u1")
        (is (some? (:ok (rooms/restart! st "u1" t0))))
        (loop/start-clock! sys code)
        (is (await-true #(> (:room/tick @(rooms/room-atom st code)) 3) 2000))
        (is (= :playing (:room/status @(rooms/room-atom st code))))
        (finally (.shutdownNow ^java.util.concurrent.ExecutorService (:executor sys))))))
  (testing "Sin reinicio, el reloj de una partida terminada se retira solo"
    (let [sys (clock-system) st (:rooms sys) code (playing-room! sys)]
      (try
        (loop/start-clock! sys code)
        (swap! (rooms/room-atom st code) assoc :room/status :finished)
        (is (await-true #(nil? (rooms/clock st code)) 2000))
        (finally (.shutdownNow ^java.util.concurrent.ExecutorService (:executor sys))))))
  (testing "QA-02. Eliminar la sala cancela su reloj"
    (let [sys (clock-system) st (:rooms sys) code (playing-room! sys)]
      (try
        (loop/start-clock! sys code)
        (let [^java.util.concurrent.Future f (rooms/clock st code)]
          (rooms/remove-room! st code)
          (is (.isCancelled f)))
        (finally (.shutdownNow ^java.util.concurrent.ExecutorService (:executor sys)))))))

(deftest ^:S02 union-a-sala-eliminada
  (testing "QA-03. Si la sala se elimina durante join!, la respuesta es :room-not-found"
    (let [st (rooms/new-state)
          code (:ok (rooms/create! st "u1" "Ana" t0))
          join-room lobby/join-room]
      (with-redefs [lobby/join-room (fn [r opts] (rooms/remove-room! st code) (join-room r opts))]
        (is (= {:error :room-not-found} (rooms/join! st "u2" code "Beto" t0))))
      (is (nil? (rooms/code-of st "u2"))))))

(deftest ^:S02 limite-de-entradas
  (let [sys (fake-system)]
    (testing "T16. 30 eventos por segundo; el 31.º se descarta"
      (is (every? #(= {:t %} (dispatch! sys "u7" [:razti/echo {:t %}])) (range 30)))
      (is (= :none (dispatch! sys "u7" [:razti/echo {:t 30}]))))
    (testing "Otro uid no se ve afectado"
      (is (= {:t 1} (dispatch! sys "u8" [:razti/echo {:t 1}]))))
    (testing "Pasado el segundo, vuelve a aceptar"
      (is (ws/allow-event? (:limiter sys) "u7" (+ t0 1001))))
    (testing "SEC-02. Los eventos inválidos también cuentan para el límite"
      (dotimes [_ 30] (dispatch! sys "u9" [:razti/create-room {:name "x" :extra 1}]))
      (is (= :none (dispatch! sys "u9" [:razti/echo {:t 1}]))))
    (testing "SEC-02. Al cerrar la conexión y en el barrido, el limiter se purga"
      (dispatch! sys "u8" [:chsk/uidport-close nil])
      (is (not (contains? @(:limiter sys) "u8")))
      (ws/purge-limiter! (:limiter sys) (+ t0 5000))
      (is (empty? @(:limiter sys))))))

(deftest ^:S02 tope-global-de-eventos
  (testing "SEC-03. Muchas sesiones no evaden el tope global por segundo"
    (let [limiter (atom {})
          results (mapv #(ws/allow-event? limiter (str "s" %) t0)
                        (range (inc config/max-events-per-second-global)))]
      (is (every? true? (butlast results)))
      (is (false? (last results))))))
