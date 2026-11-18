(ns razti.shared.campus-test
  (:require [clojure.test :refer [deftest is testing]]
            [razti.shared.campus :as campus]
            [razti.shared.config :as config]
            [razti.shared.grid :as grid]))

(def ^:private g campus/INITIAL-GRID)

(defn- playable? [[x y]] (and (<= 2 x 17) (<= 3 y 17)))

(deftest ^:S01 convenciones-del-mapa
  (testing "Tamaño 20×20 y valores 0, 1 o 2"
    (is (= config/grid-rows (count g)))
    (is (every? #(= config/grid-cols (count %)) g))
    (is (every? #{0 1 2} (flatten g))))
  (testing "Avenidas y muro (C27): filas 0–2 y 18–19, columnas 0–1 y 18–19 son hard-block"
    (is (every? (fn [[x y]] (= 1 (grid/cell g [x y])))
                (for [y (range 20) x (range 20) :when (not (playable? [x y]))] [x y]))))
  (testing "4 apariciones libres, dentro del área jugable, con 2 o más vecinos vacíos"
    (is (= config/max-players (count campus/SPAWNS)))
    (is (= (count campus/SPAWNS) (count (set campus/SPAWNS))))
    (doseq [s campus/SPAWNS]
      (is (playable? s) (str s))
      (is (grid/empty-cell? g s) (str s))
      (is (<= 2 (count (filter #(grid/empty-cell? g %) (grid/neighbors s)))) (str s))))
  (testing "Cada aparición alcanza a las demás por celdas 0 o 2"
    (doseq [a campus/SPAWNS b campus/SPAWNS :when (not= a b)]
      (is (grid/reachable? g a b) (str a " → " b)))))
