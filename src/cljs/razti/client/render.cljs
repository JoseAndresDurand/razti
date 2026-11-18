(ns razti.client.render
  "Canvas 2D a 60 FPS con requestAnimationFrame (TDD M2/S02 §4; núcleo T20)."
  (:require [razti.shared.config :as config]))

(def ^:private cell config/cell-px)
(def ^:private dir->row {:down 0 :up 1 :right 2 :left 2})

;; ---------------------------------------------------------------------------
;; Assets

(defonce ^:private assets (atom {:map nil :sheets nil}))

(defn- hex->rgb [hex]
  [(js/parseInt (subs hex 1 3) 16) (js/parseInt (subs hex 3 5) 16) (js/parseInt (subs hex 5 7) 16)])

(defn- rgb->hsl [r g b]
  (let [r (/ r 255) g (/ g 255) b (/ b 255)
        mx (max r g b) mn (min r g b)
        l (/ (+ mx mn) 2)
        d (- mx mn)]
    (if (zero? d)
      [0 0 l]
      (let [s (if (> l 0.5) (/ d (- 2 mx mn)) (/ d (+ mx mn)))
            h (cond (= mx r) (+ (/ (- g b) d) (if (< g b) 6 0))
                    (= mx g) (+ (/ (- b r) d) 2)
                    :else (+ (/ (- r g) d) 4))]
        [(* 60 h) s l]))))

(defn- hue->rgb [p q t]
  (let [t (cond (neg? t) (inc t) (> t 1) (dec t) :else t)]
    (cond (< t (/ 1 6)) (+ p (* (- q p) 6 t))
          (< t 0.5) q
          (< t (/ 2 3)) (+ p (* (- q p) (- (/ 2 3) t) 6))
          :else p)))

(defn- hsl->rgb [h s l]
  (if (zero? s)
    (let [v (js/Math.round (* l 255))] [v v v])
    (let [q (if (< l 0.5) (* l (+ 1 s)) (- (+ l s) (* l s)))
          p (- (* 2 l) q)
          h (/ h 360)]
      (mapv #(js/Math.round (* 255 %))
            [(hue->rgb p q (+ h (/ 1 3))) (hue->rgb p q h) (hue->rgb p q (- h (/ 1 3)))]))))

(defn- tinted-sheet
  "Copia de la hoja con los azules (tono 200°–250°, saturación > 0.25)
  cambiados al tono de `hex`, conservando la luminosidad (T20)."
  [img hex]
  (let [w (.-width img) h (.-height img)
        c (doto (.createElement js/document "canvas") (set! -width w) (set! -height h))
        ctx (.getContext c "2d")
        _ (.drawImage ctx img 0 0)
        data (.getImageData ctx 0 0 w h)
        px (.-data data)
        [target-h _ _] (apply rgb->hsl (hex->rgb hex))]
    (loop [i 0]
      (when (< i (.-length px))
        (when (pos? (aget px (+ i 3)))
          (let [[hh s l] (rgb->hsl (aget px i) (aget px (+ i 1)) (aget px (+ i 2)))]
            (when (and (<= 200 hh 250) (> s 0.25))
              (let [[r g b] (hsl->rgb target-h s l)]
                (aset px i r) (aset px (+ i 1) g) (aset px (+ i 2) b)))))
        (recur (+ i 4))))
    (.putImageData ctx data 0 0)
    c))

(defn- load-image [src on-load]
  (let [img (js/Image.)]
    (set! (.-onload img) #(on-load img))
    (set! (.-src img) src)))

(defn load-assets! []
  (load-image "/assets/map-campus.jpeg" #(swap! assets assoc :map %))
  (load-image "/assets/player-sprites.png"
              (fn [img]
                (swap! assets assoc :sheets
                       (into [img] (map #(tinted-sheet img %) (rest config/player-colors)))))))

;; ---------------------------------------------------------------------------
;; Capas

(defonce ^:private motion (atom {})) ; uid → {:pos [x y] :moved-at ms}

(defn- track-motion! [players now]
  (swap! motion
         (fn [m]
           (reduce-kv (fn [acc id {:keys [pos]}]
                        (if (= pos (get-in acc [id :pos]))
                          acc
                          (assoc acc id {:pos pos :moved-at now})))
                      m players))))

(defn- draw-soft-blocks [ctx grid]
  (doseq [y (range (count grid)) x (range (count (nth grid y)))
          :when (= 2 (get-in grid [y x]))]
    (let [px (* x cell) py (* y cell)]
      (set! (.-fillStyle ctx) "#8B5A2B")
      (.fillRect ctx (+ px 3) (+ py 3) (- cell 6) (- cell 6))
      (set! (.-strokeStyle ctx) "#5C3A1A")
      (set! (.-lineWidth ctx) 3)
      (.strokeRect ctx (+ px 4.5) (+ py 4.5) (- cell 9) (- cell 9))
      (.beginPath ctx)
      (.moveTo ctx (+ px 6) (+ py 6))
      (.lineTo ctx (- (+ px cell) 6) (- (+ py cell) 6))
      (.stroke ctx))))

(defn- draw-bombs [ctx bombs now]
  (doseq [{[x y] :pos :keys [ticks-left]} bombs]
    (let [cx (+ (* x cell) (/ cell 2)) cy (+ (* y cell) (/ cell 2))
          period (+ 60 (* 8 ticks-left))]
      (set! (.-fillStyle ctx) "#000000")
      (.beginPath ctx)
      (.arc ctx cx (+ cy 2) 14 0 (* 2 js/Math.PI))
      (.fill ctx)
      (when (even? (js/Math.floor (/ now period)))
        (set! (.-fillStyle ctx) "#FFB000")
        (.beginPath ctx)
        (.arc ctx (+ cx 10) (- cy 12) 4 0 (* 2 js/Math.PI))
        (.fill ctx)))))

(defn- draw-blasts [ctx blasts]
  (doseq [{:keys [cells]} blasts [x y] cells]
    (let [px (* x cell) py (* y cell)]
      (set! (.-fillStyle ctx) "#FFB000")
      (.fillRect ctx px py cell cell)
      (set! (.-fillStyle ctx) "#FFF2A8")
      (.fillRect ctx (+ px 10) (+ py 10) (- cell 20) (- cell 20)))))

(defn- draw-players [ctx players sheets now]
  (doseq [[id {[x y] :pos :keys [dir alive? join-order name]}] players
          :when (and alive? x)]
    (let [px (* x cell) py (* y cell)
          sheet (nth sheets (mod join-order (count sheets)) nil)
          moved-at (get-in @motion [id :moved-at] 0)
          frame (if (< (- now moved-at) 200) (inc (mod (js/Math.floor (/ now 150)) 2)) 0)
          row (dir->row dir 0)]
      (when sheet
        (if (= dir :left)
          (do (.save ctx)
              (.translate ctx (+ px cell) py)
              (.scale ctx -1 1)
              (.drawImage ctx sheet (* frame 40) (* row 40) 40 40 0 0 cell cell)
              (.restore ctx))
          (.drawImage ctx sheet (* frame 40) (* row 40) 40 40 px py cell cell)))
      (set! (.-font ctx) "bold 12px system-ui, sans-serif")
      (set! (.-textAlign ctx) "center")
      (set! (.-lineWidth ctx) 3)
      (set! (.-strokeStyle ctx) "#000000")
      (.strokeText ctx name (+ px (/ cell 2)) (- py 2))
      (set! (.-fillStyle ctx) "#FFFFFF")
      (.fillText ctx name (+ px (/ cell 2)) (- py 2)))))

(defn- draw-grid-overlay
  "C28: líneas de la grilla y celdas bloqueadas en rojo translúcido."
  [ctx grid]
  (set! (.-fillStyle ctx) "rgba(255,0,0,0.35)")
  (doseq [y (range (count grid)) x (range (count (nth grid y)))
          :when (= 1 (get-in grid [y x]))]
    (.fillRect ctx (* x cell) (* y cell) cell cell))
  (set! (.-strokeStyle ctx) "rgba(255,255,0,0.6)")
  (set! (.-lineWidth ctx) 1)
  (doseq [i (range (inc config/grid-cols))]
    (.beginPath ctx)
    (.moveTo ctx (+ 0.5 (* i cell)) 0)
    (.lineTo ctx (+ 0.5 (* i cell)) config/canvas-px)
    (.stroke ctx)
    (.beginPath ctx)
    (.moveTo ctx 0 (+ 0.5 (* i cell)))
    (.lineTo ctx config/canvas-px (+ 0.5 (* i cell)))
    (.stroke ctx)))

(defn draw!
  "Dibuja el estado actual: mapa → soft-blocks → bombas → blasts → jugadores → overlay."
  [ctx {:keys [snapshot grid debug-grid?]} now]
  (set! (.-imageSmoothingEnabled ctx) false)
  (let [{:keys [map sheets]} @assets]
    (if map
      (.drawImage ctx map 0 0 config/canvas-px config/canvas-px)
      (do (set! (.-fillStyle ctx) "#14161A")
          (.fillRect ctx 0 0 config/canvas-px config/canvas-px)))
    (when grid (draw-soft-blocks ctx grid))
    (when snapshot
      (track-motion! (:players snapshot) now)
      (draw-bombs ctx (:bombs snapshot) now)
      (draw-blasts ctx (:blasts snapshot))
      (draw-players ctx (:players snapshot) (or sheets []) now))
    (when (and debug-grid? grid) (draw-grid-overlay ctx grid))))

(defn start!
  "Bucle de requestAnimationFrame; dibuja solo en la pantalla de juego."
  [app canvas]
  (let [ctx (.getContext canvas "2d")]
    (letfn [(frame [now]
              (when (= :game (:screen @app))
                (draw! ctx @app now))
              (js/requestAnimationFrame frame))]
      (js/requestAnimationFrame frame))))
