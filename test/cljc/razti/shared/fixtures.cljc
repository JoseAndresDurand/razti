(ns razti.shared.fixtures
  "Grillas mínimas y salas de prueba. Los escenarios no dependen del mapa real."
  (:require [razti.shared.grid :as grid]))

(defn mini-grid
  "Grilla w×h con borde de hard-blocks (1) e interior vacío (0).
  `cells` = {[x y] valor} sobrescribe celdas."
  ([w h] (mini-grid w h {}))
  ([w h cells]
   (reduce-kv grid/set-cell
              (vec (for [y (range h)]
                     (vec (for [x (range w)]
                            (if (or (zero? x) (zero? y) (= x (dec w)) (= y (dec h))) 1 0)))))
              cells)))

(defn player [id pos join-order]
  {:player/id id
   :player/name (str "P" (inc join-order))
   :player/pos pos
   :player/dir :down
   :player/alive? true
   :player/cooldown 0
   :player/last-seq 0
   :player/join-order join-order
   :player/connected? true})

(defn playing-room
  "Sala en :playing. `positions` = {\"p1\" [x y] …}; join-order por id ordenado."
  [g positions]
  (let [ids (sort (keys positions))]
    {:room/code "UNSA2"
     :room/status :playing
     :room/owner (first ids)
     :room/tick 0
     :room/next-id 100
     :room/grid g
     :room/initial-grid g
     :room/spawns (mapv positions ids)
     :room/grid-version 0
     :room/inputs []
     :room/players (into {} (map-indexed (fn [i id] [id (player id (positions id) i)])) ids)
     :room/bombs []
     :room/blasts []
     :room/winner nil}))

(defn bomb [id owner pos ticks-left radius]
  {:bomb/id id
   :bomb/owner owner
   :bomb/pos pos
   :bomb/ticks-left ticks-left
   :bomb/radius radius
   :bomb/pass-through #{}})

(defn with-bombs [room & bombs]
  (assoc room :room/bombs (vec bombs)))
