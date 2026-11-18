(ns razti.shared.grid
  "Consultas puras sobre la grilla. Posiciones [x y] (columna, fila);
  la grilla es un vector de filas: (get-in grid [y x]).
  Celdas: 0 vacía, 1 hard-block, 2 soft-block.")

(def dirs [:up :down :left :right])

(def dir->delta {:up [0 -1] :down [0 1] :left [-1 0] :right [1 0]})

(defn cell
  "Valor de la celda, o nil fuera de la grilla."
  [grid [x y]]
  (get-in grid [y x]))

(defn in-bounds? [grid pos]
  (some? (cell grid pos)))

(defn set-cell [grid [x y] v]
  (assoc-in grid [y x] v))

(defn step [[x y] dir]
  (let [[dx dy] (dir->delta dir)]
    [(+ x dx) (+ y dy)]))

(defn neighbors
  "Las 4 posiciones ortogonales, en el orden de `dirs`."
  [pos]
  (mapv #(step pos %) dirs))

(defn empty-cell? [grid pos]
  (= 0 (cell grid pos)))

(defn- traversable? [grid pos]
  (contains? #{0 2} (cell grid pos)))

(defn reachable?
  "true si `to` se alcanza desde `from` pasando solo por celdas 0 o 2."
  [grid from to]
  (and (traversable? grid from)
       (traversable? grid to)
       (loop [frontier #{from}
              seen #{from}]
         (cond
           (contains? seen to) true
           (empty? frontier) false
           :else (let [nxt (into #{}
                                 (comp (mapcat neighbors)
                                       (filter #(traversable? grid %))
                                       (remove seen))
                                 frontier)]
                   (recur nxt (into seen nxt)))))))
