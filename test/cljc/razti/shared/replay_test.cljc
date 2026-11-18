(ns razti.shared.replay-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [razti.shared.engine :as engine]
            [razti.shared.fixtures :as f]
            [razti.shared.specs :as specs]))

(def ^:private ids ["p1" "p2" "p3" "p4"])

(def ^:private initial
  (f/playing-room (f/mini-grid 7 7 {[3 3] 2 [2 4] 2 [4 2] 2 [3 1] 1})
                  {"p1" [1 1] "p2" [5 1] "p3" [1 5] "p4" [5 5]}))

(def ^:private gen-input
  (gen/one-of
   [(gen/hash-map :player-id (gen/elements ids)
                  :action (gen/return :move)
                  :dir (gen/elements [:up :down :left :right])
                  :seq gen/nat)
    (gen/hash-map :player-id (gen/elements ids)
                  :action (gen/return :bomb)
                  :seq gen/nat)]))

(def ^:private gen-ticks
  (gen/vector (gen/vector gen-input 0 4) 0 60))

(defn- run
  "Todos los estados, del inicial al final, tras encolar y ejecutar cada tick."
  [room ticks]
  (reductions (fn [r inputs]
                (engine/tick-game (reduce engine/enqueue-input r inputs)))
              room
              ticks))

;; Escenario 7 del brief §8: mismas entradas → mismo estado.
(defspec ^:S01 escenario-7-replay 100
  (prop/for-all [ticks gen-ticks]
                (let [a (run initial ticks)
                      b (run initial ticks)]
                  (and (= (last a) (last b))
                       (every? specs/valid-room? a)))))
