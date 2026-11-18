(ns razti.shared.engine-test
  (:require [clojure.test :refer [deftest is testing]]
            [razti.shared.engine :as engine]
            [razti.shared.fixtures :as f]
            [razti.shared.grid :as grid]))

(defn- pos-of [room id] (get-in room [:room/players id :player/pos]))
(defn- alive-of [room id] (get-in room [:room/players id :player/alive?]))

(defn- ticks [room n] (nth (iterate engine/tick-game room) n))

(defn- step-with
  "Encola las entradas y ejecuta un tick."
  [room & inputs]
  (engine/tick-game (reduce engine/enqueue-input room inputs)))

(defn- mv [id dir] {:player-id id :action :move :dir dir})
(defn- bomb-in [id] {:player-id id :action :bomb})

;; ---------------------------------------------------------------------------
;; Escenarios BDD del brief §8

(deftest ^:S01 escenario-1-movimiento
  (testing "1. Movimiento válido e inválido"
    (let [room (f/playing-room (f/mini-grid 5 5) {"p1" [1 1] "p2" [3 3]})]
      (is (= 0 (grid/cell (:room/grid room) [1 2])))
      (is (= 1 (grid/cell (:room/grid room) [0 1])))
      (is (= [1 2] (pos-of (engine/apply-input room (mv "p1" :down)) "p1")))
      (is (= [1 1] (pos-of (engine/apply-input room (mv "p1" :left)) "p1"))))))

(deftest ^:S01 escenario-2-detonacion
  (testing "2. Detonación, destrucción de soft-block y eliminación"
    (let [g (f/mini-grid 5 5 {[1 2] 1 [2 1] 1 [2 3] 2})
          room (-> (f/playing-room g {"p1" [1 3] "p2" [3 2]})
                   (f/with-bombs (f/bomb "b-a" "p1" [2 2] 1 1)))
          after (engine/tick-game room)
          blasts (:room/blasts after)]
      (is (empty? (:room/bombs after)))
      (is (= 1 (count blasts)))
      (is (= #{[2 2] [2 3] [3 2]} (set (:blast/cells (first blasts)))))
      (is (= 3 (count (:blast/cells (first blasts)))))
      (is (= 0 (grid/cell (:room/grid after) [2 3])))
      (is (false? (alive-of after "p2"))))))

(deftest ^:S01 escenario-4-cadena
  (testing "4. Reacción en cadena (R7)"
    (let [g (f/mini-grid 7 5)
          room (-> (f/playing-room g {"p1" [1 1] "p2" [5 3]})
                   (f/with-bombs (f/bomb "b-a" "p1" [2 2] 1 2)
                     (f/bomb "b-b" "p2" [4 2] 40 2)))
          after (engine/tick-game room)]
      (is (= 0 (grid/cell g [3 2])))
      (is (= 2 (count (:room/blasts after))))
      (is (empty? (:room/bombs after)))
      (is (= :playing (:room/status after))))))

(deftest ^:S01 escenario-5-salida-bomba
  (testing "5. Salida de la bomba propia (R9) y límite de bombas (R3)"
    (let [room (f/playing-room (f/mini-grid 5 5) {"p1" [1 1] "p2" [3 3]})
          r1 (step-with room (bomb-in "p1") (bomb-in "p1"))
          _ (is (= 1 (count (:room/bombs r1))))
          _ (is (= [1 1] (:bomb/pos (first (:room/bombs r1)))))
          r2 (step-with r1 (mv "p1" :down))
          _ (is (= [1 2] (pos-of r2 "p1")))
          r3 (ticks r2 4)
          _ (is (= 0 (get-in r3 [:room/players "p1" :player/cooldown])))
          r4 (step-with r3 (mv "p1" :up))]
      (is (= [1 2] (pos-of r4 "p1")))
      (is (= 1 (count (:room/bombs r4)))))))

(deftest ^:S01 escenario-6-empate
  (testing "6. Empate"
    (let [room (-> (f/playing-room (f/mini-grid 5 5) {"p1" [1 2] "p2" [3 2]})
                   (f/with-bombs (f/bomb "b-a" "p1" [2 2] 1 1)))
          after (engine/tick-game room)]
      (is (= :finished (:room/status after)))
      (is (= :draw (:room/winner after))))))

;; ---------------------------------------------------------------------------
;; Reglas decididas (TDD §1.5)

(deftest ^:S01 t1-cooldown
  (testing "T1: tras moverse en t, el siguiente movimiento ocurre en t+4"
    (let [room (f/playing-room (f/mini-grid 7 7) {"p1" [1 1] "p2" [5 5]})
          a (step-with room (mv "p1" :right))
          b (nth (iterate #(step-with % (mv "p1" :right)) a) 3)
          c (step-with b (mv "p1" :right))]
      (is (= [2 1] (pos-of a "p1")))
      (is (= [2 1] (pos-of b "p1")))
      (is (= [3 1] (pos-of c "p1"))))))

(deftest ^:S01 t1-intento-bloqueado-orienta
  (testing "T1: un intento bloqueado cambia la dirección y no consume cooldown"
    (let [room (f/playing-room (f/mini-grid 5 5) {"p1" [1 1] "p2" [3 3]})
          after (engine/apply-input room (mv "p1" :up))]
      (is (= [1 1] (pos-of after "p1")))
      (is (= :up (get-in after [:room/players "p1" :player/dir])))
      (is (= 0 (get-in after [:room/players "p1" :player/cooldown]))))))

(deftest ^:S01 t2-blast-atraviesa-bomba
  (testing "T2: el blast detona la bomba y sigue"
    (let [room (-> (f/playing-room (f/mini-grid 7 5) {"p1" [5 1] "p2" [5 3]})
                   (f/with-bombs (f/bomb "b-a" "p1" [1 2] 1 3)
                     (f/bomb "b-b" "p2" [2 2] 40 1)))
          after (engine/tick-game room)
          cells-a (set (:blast/cells (first (:room/blasts after))))]
      (is (contains? cells-a [3 2]))
      (is (contains? cells-a [4 2]))
      (is (empty? (:room/bombs after))))))

(deftest ^:S01 t3-grilla-anterior
  (testing "T3: dos blasts del mismo tick se detienen en el mismo soft-block"
    (let [g (f/mini-grid 7 5 {[3 2] 2})
          room (-> (f/playing-room g {"p1" [1 1] "p2" [5 1]})
                   (f/with-bombs (f/bomb "b-a" "p1" [2 2] 1 2)
                     (f/bomb "b-b" "p2" [4 2] 1 2)))
          after (engine/tick-game room)
          [bl-a bl-b] (:room/blasts after)]
      (is (not (contains? (set (:blast/cells bl-a)) [4 2])))
      (is (not (contains? (set (:blast/cells bl-b)) [2 2])))
      (is (= 0 (grid/cell (:room/grid after) [3 2])))
      (is (= 1 (:room/grid-version after))))))

(deftest ^:S01 r6-hard-block
  (testing "R6: el hard-block detiene el blast sin incluirse"
    (let [g (f/mini-grid 7 3 {[3 1] 1})]
      (is (= [[1 1] [2 1]] (engine/blast-cells g [1 1] 3))))))

(deftest ^:S01 t4-ids
  (testing "T4: bombas y blasts comparten el contador de ids"
    (let [room (f/playing-room (f/mini-grid 7 7) {"p1" [1 1] "p2" [5 5]})
          placed (engine/place-bomb room "p1")
          blown (-> placed
                    (assoc-in [:room/bombs 0 :bomb/ticks-left] 1)
                    engine/tick-game)]
      (is (= "b-100" (:bomb/id (first (:room/bombs placed)))))
      (is (= "bl-101" (:blast/id (first (:room/blasts blown)))))
      (is (= 102 (:room/next-id blown))))))

(deftest ^:S01 r3-limite-en-otra-celda
  (testing "R3: con una bomba activa no se coloca otra, aunque sea en otra celda"
    (let [room (f/playing-room (f/mini-grid 5 5) {"p1" [1 1] "p2" [3 3]})
          r1 (-> (step-with room (bomb-in "p1"))
                 (step-with (mv "p1" :down))
                 (ticks 4))
          r2 (step-with r1 (bomb-in "p1"))]
      (is (= [1 2] (pos-of r1 "p1")))
      (is (= 1 (count (:room/bombs r2))))
      (is (= [1 1] (:bomb/pos (first (:room/bombs r2))))))))

(deftest ^:S01 d8-bomba-sobre-bomba
  (testing "D8: una bomba por celda"
    (let [room (f/playing-room (f/mini-grid 5 5) {"p1" [1 1] "p2" [1 1]})
          after (-> room (engine/place-bomb "p1") (engine/place-bomb "p2"))
          bomb (first (:room/bombs after))]
      (is (= 1 (count (:room/bombs after))))
      (is (= "p1" (:bomb/owner bomb)))
      (is (= #{"p1" "p2"} (:bomb/pass-through bomb))))))

(deftest ^:S01 r8-entrar-en-blast
  (testing "R8: entrar en un blast activo elimina al jugador"
    (let [room (assoc (f/playing-room (f/mini-grid 5 5) {"p1" [1 1] "p2" [3 3]})
                      :room/blasts [{:blast/id "bl-x" :blast/cells [[2 1]] :blast/ticks-left 5}])
          after (step-with room (mv "p1" :right))]
      (is (= [2 1] (pos-of after "p1")))
      (is (false? (alive-of after "p1"))))))

(deftest ^:S01 r11-desconexion
  (testing "R11/T7: :leave elimina en el tick y da la victoria al otro"
    (let [room (f/playing-room (f/mini-grid 5 5) {"p1" [1 1] "p2" [3 3]})
          after (step-with room {:player-id "p1" :action :leave})]
      (is (false? (alive-of after "p1")))
      (is (= :finished (:room/status after)))
      (is (= "p2" (:room/winner after))))))

(deftest ^:S01 t6-entradas-ignoradas
  (testing "T6: entradas de un muerto o fuera de :playing se ignoran"
    (let [room (-> (f/playing-room (f/mini-grid 5 5) {"p1" [1 1] "p2" [3 3] "p3" [3 1]})
                   (assoc-in [:room/players "p1" :player/alive?] false))
          after (step-with room (mv "p1" :down) (bomb-in "p1"))
          waiting (assoc room :room/status :waiting)]
      (is (= [1 1] (pos-of after "p1")))
      (is (empty? (:room/bombs after)))
      (is (= [] (:room/inputs (engine/enqueue-input waiting (mv "p2" :up))))))))

(deftest ^:S01 last-seq
  (testing "C12: el último número de secuencia procesado se registra"
    (let [room (f/playing-room (f/mini-grid 5 5) {"p1" [1 1] "p2" [3 3]})
          after (step-with room (assoc (mv "p1" :down) :seq 7))]
      (is (= 7 (get-in after [:room/players "p1" :player/last-seq]))))))
