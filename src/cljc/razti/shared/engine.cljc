(ns razti.shared.engine
  "Reglas puras del juego (brief §5.1, TDD M1/S01 §4). Estado → estado."
  (:require [razti.shared.config :as config]
            [razti.shared.grid :as grid]))

;; ---------------------------------------------------------------------------
;; Consultas

(defn- player [room id]
  (get-in room [:room/players id]))

(defn- alive? [room id]
  (true? (:player/alive? (player room id))))

(defn- bomb-at [room pos]
  (some #(when (= pos (:bomb/pos %)) %) (:room/bombs room)))

(defn- bombs-of [room id]
  (count (filter #(= id (:bomb/owner %)) (:room/bombs room))))

;; ---------------------------------------------------------------------------
;; Entradas

(defn enqueue-input
  "Encola una entrada para el próximo tick (C8). Fuera de :playing no encola (T6)."
  [room input]
  (if (= :playing (:room/status room))
    (update room :room/inputs conj input)
    room))

(defn move-player
  "R2, R9, T1. El intento siempre orienta al jugador; solo un movimiento
  efectivo consume el cooldown."
  [room player-id dir]
  (if-not (alive? room player-id)
    room
    (let [room (assoc-in room [:room/players player-id :player/dir] dir)
          {:player/keys [pos cooldown]} (player room player-id)
          target (grid/step pos dir)
          bomb (bomb-at room target)
          blocked? (or (pos? cooldown)
                       (not (grid/empty-cell? (:room/grid room) target))
                       (and bomb (not (contains? (:bomb/pass-through bomb) player-id))))]
      (if blocked?
        room
        (-> room
            (update-in [:room/players player-id] assoc
                       :player/pos target
                       :player/cooldown config/move-cooldown-ticks)
            (update :room/bombs
                    (fn [bombs]
                      (mapv #(if (= pos (:bomb/pos %))
                               (update % :bomb/pass-through disj player-id)
                               %)
                            bombs))))))))

(defn place-bomb
  "R3, R9, D8. Una bomba por jugador y una por celda."
  [room player-id]
  (let [pos (:player/pos (player room player-id))]
    (if (or (not (alive? room player-id))
            (>= (bombs-of room player-id) config/max-bombs-per-player)
            (bomb-at room pos))
      room
      (let [n (:room/next-id room)
            here (into #{}
                       (comp (filter :player/alive?)
                             (filter #(= pos (:player/pos %)))
                             (map :player/id))
                       (vals (:room/players room)))]
        (-> room
            (update :room/bombs conj {:bomb/id (str "b-" n)
                                      :bomb/owner player-id
                                      :bomb/pos pos
                                      :bomb/ticks-left config/bomb-fuse-ticks
                                      :bomb/radius config/bomb-radius
                                      :bomb/pass-through here})
            (assoc :room/next-id (inc n)))))))

(defn apply-input
  "Aplica una entrada (:move | :bomb | :leave). Las de jugadores muertos o
  inexistentes se ignoran (T6). :leave elimina al jugador (R11, T7)."
  [room input]
  (let [{:keys [player-id action dir]} input]
    (if-not (alive? room player-id)
      room
      (let [room (case action
                   :move (move-player room player-id dir)
                   :bomb (place-bomb room player-id)
                   :leave (assoc-in room [:room/players player-id :player/alive?] false)
                   room)]
        (if-let [s (:seq input)]
          (assoc-in room [:room/players player-id :player/last-seq] s)
          room)))))

;; ---------------------------------------------------------------------------
;; Explosiones

(defn blast-cells
  "R6, T2: cruz desde `origin` hasta `radius`. Un hard-block (o el borde)
  la detiene sin incluirse; un soft-block se incluye y la detiene."
  [g origin radius]
  (into [origin]
        (mapcat (fn [dir]
                  (loop [i 1
                         pos (grid/step origin dir)
                         acc []]
                    (if (> i radius)
                      acc
                      (case (grid/cell g pos)
                        0 (recur (inc i) (grid/step pos dir) (conj acc pos))
                        2 (conj acc pos)
                        acc)))))
        grid/dirs))

(defn- detonate
  "R7, T3, T4: detona las bombas vencidas y, en el mismo tick, las que
  alcance un blast. Todas las celdas se calculan sobre la grilla anterior;
  los soft-blocks alcanzados se destruyen al final."
  [room]
  (let [g (:room/grid room)
        bombs (:room/bombs room)]
    (loop [pending (filterv #(<= (:bomb/ticks-left %) 0) bombs)
           done #{}
           new-blasts []
           n (:room/next-id room)]
      (if-let [b (first pending)]
        (if (contains? done (:bomb/id b))
          (recur (subvec pending 1) done new-blasts n)
          (let [cells (blast-cells g (:bomb/pos b) (:bomb/radius b))
                cell-set (set cells)
                done (conj done (:bomb/id b))
                hit (filterv #(and (contains? cell-set (:bomb/pos %))
                                   (not (contains? done (:bomb/id %))))
                             bombs)]
            (recur (into (subvec pending 1) hit)
                   done
                   (conj new-blasts {:blast/id (str "bl-" n)
                                     :blast/cells cells
                                     :blast/ticks-left config/blast-ticks})
                   (inc n))))
        (let [soft (into #{}
                         (comp (mapcat :blast/cells)
                               (filter #(= 2 (grid/cell g %))))
                         new-blasts)
              g' (reduce #(grid/set-cell %1 %2 0) g soft)]
          (cond-> (assoc room
                         :room/bombs (filterv #(not (contains? done (:bomb/id %))) bombs)
                         :room/blasts (into (:room/blasts room) new-blasts)
                         :room/next-id n
                         :room/grid g')
            (seq soft) (update :room/grid-version inc)))))))

(defn- apply-damage
  "R8: muere todo jugador vivo que esté en una celda de un blast activo."
  [room]
  (let [hot (into #{} (mapcat :blast/cells) (:room/blasts room))]
    (update room :room/players
            update-vals
            (fn [p]
              (if (and (:player/alive? p) (contains? hot (:player/pos p)))
                (assoc p :player/alive? false)
                p)))))

(defn- evaluate-victory
  "T5: 1 vivo gana; 0 vivos es empate."
  [room]
  (let [alive (filter :player/alive? (vals (:room/players room)))]
    (case (count alive)
      0 (assoc room :room/status :finished :room/winner :draw)
      1 (assoc room :room/status :finished :room/winner (:player/id (first alive)))
      room)))

;; ---------------------------------------------------------------------------
;; Tick

(defn tick-game
  "Avanza un tick (R10, T1). No-op fuera de :playing."
  [room]
  (if (not= :playing (:room/status room))
    room
    (let [inputs (:room/inputs room)]
      (as-> room r
        ;; 0. cooldowns (T1)
        (update r :room/players update-vals
                #(update % :player/cooldown (fn [c] (max 0 (dec c)))))
        ;; 1. entradas en orden de llegada
        (reduce apply-input (assoc r :room/inputs []) inputs)
        ;; 2. mechas
        (update r :room/bombs (fn [bs] (mapv #(update % :bomb/ticks-left dec) bs)))
        ;; 3. detonación con cadena
        (detonate r)
        ;; 4. daño
        (apply-damage r)
        ;; 5. duración de los blasts
        (update r :room/blasts
                (fn [bls]
                  (into []
                        (comp (map #(update % :blast/ticks-left dec))
                              (filter #(pos? (:blast/ticks-left %))))
                        bls)))
        ;; 6. victoria
        (evaluate-victory r)
        ;; 7. reloj
        (update r :room/tick inc)))))
