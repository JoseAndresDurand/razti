(ns assets
  "Script de assets (brief C26; TDD M1/S01 §4). Solo JDK.

  Uso: clojure -M:assets [all|map|sprites|preview] [--src <dir>]
    map      map-2.jpeg       → resources/public/assets/map-campus.jpeg (800×800)
    sprites  personaje-1.jpeg → resources/public/assets/player-sprites.png (120×120, alfa)
    preview  map-campus.jpeg + campus/INITIAL-GRID + SPAWNS → target/grid-preview.png
  Por defecto: all y --src ../1-files/img (los originales viven fuera del repo)."
  (:require [clojure.java.io :as io])
  (:import (java.awt AlphaComposite BasicStroke Color Font Graphics2D RenderingHints)
           (java.awt.image BufferedImage)
           (java.io File)
           (javax.imageio IIOImage ImageIO ImageWriteParam ImageWriter)
           (javax.imageio.stream ImageOutputStream)))

(set! *warn-on-reflection* true)

(def ^:private assets-dir "resources/public/assets")
(def ^:private canvas 800)
(def ^:private cell 40)
(def ^:private frame 40)        ; cuadro de la hoja de sprites
(def ^:private sprite-h 36)     ; alto máximo del personaje (2 px de margen)
(def ^:private feet-y 38)       ; los pies terminan en y = 38
(def ^:private bg-tolerance 40)

;; ---------------------------------------------------------------------------
;; E/S

(defn- read-image ^BufferedImage [^File f]
  (or (ImageIO/read f)
      (throw (ex-info (str "No se pudo leer " f) {:file (str f)}))))

(defn- write-jpeg [^BufferedImage img ^File f quality]
  (io/make-parents f)
  (.delete f)
  (let [^ImageWriter w (.next (ImageIO/getImageWritersByFormatName "jpeg"))
        p (doto (.getDefaultWriteParam w)
            (.setCompressionMode ImageWriteParam/MODE_EXPLICIT)
            (.setCompressionQuality (float quality)))]
    (with-open [^ImageOutputStream os (ImageIO/createImageOutputStream f)]
      (.setOutput w os)
      (.write w nil (IIOImage. img nil nil) p))
    (.dispose w)))

(defn- write-png [^BufferedImage img ^File f]
  (io/make-parents f)
  (.delete f)
  (ImageIO/write img "png" f))

(defn- rgb [^long px] [(bit-and (bit-shift-right px 16) 0xff)
                       (bit-and (bit-shift-right px 8) 0xff)
                       (bit-and px 0xff)])

;; ---------------------------------------------------------------------------
;; Mapa: reducción por promedio de área

(defn- downscale-area ^BufferedImage [^BufferedImage src ^long w ^long h]
  (let [sw (.getWidth src)
        sh (.getHeight src)
        out (BufferedImage. w h BufferedImage/TYPE_INT_RGB)]
    (dotimes [ty h]
      (let [y0 (quot (* ty sh) h)
            y1 (max (inc y0) (quot (* (inc ty) sh) h))]
        (dotimes [tx w]
          (let [x0 (quot (* tx sw) w)
                x1 (max (inc x0) (quot (* (inc tx) sw) w))
                [r g b n] (reduce (fn [[r g b n] [x y]]
                                    (let [[pr pg pb] (rgb (.getRGB src (int x) (int y)))]
                                      [(+ r pr) (+ g pg) (+ b pb) (inc n)]))
                                  [0 0 0 0]
                                  (for [y (range y0 y1) x (range x0 x1)] [x y]))]
            (.setRGB out tx ty (unchecked-int (bit-or (bit-shift-left (quot r n) 16)
                                                      (bit-shift-left (quot g n) 8)
                                                      (quot b n))))))))
    out))

(defn- make-map [src-dir]
  (let [src (read-image (io/file src-dir "map-2.jpeg"))
        out (io/file assets-dir "map-campus.jpeg")]
    (write-jpeg (downscale-area src canvas canvas) out 0.92)
    (println "map     →" (str out))))

;; ---------------------------------------------------------------------------
;; Hoja de sprites: fondo → alfa, recorte, escala única, vecino más cercano

(defn- median [xs] (nth (sort xs) (quot (count xs) 2)))

(defn- background-color [^BufferedImage img]
  (let [w (.getWidth img) h (.getHeight img)
        corners [[0 0] [(- w 16) 0] [0 (- h 16)] [(- w 16) (- h 16)]]
        pixels (for [[cx cy] corners dx (range 16) dy (range 16)]
                 (rgb (.getRGB img (int (+ cx dx)) (int (+ cy dy)))))]
    (mapv (fn [i] (median (map #(nth % i) pixels))) (range 3))))

(defn- opaque? [^BufferedImage img bg x y]
  (let [c (rgb (.getRGB img (int x) (int y)))]
    (> (apply max (map #(Math/abs (long (- %1 %2))) c bg)) bg-tolerance)))

(defn- bbox
  "Rectángulo mínimo [x0 y0 x1 y1] (inclusivo) de los píxeles opacos de la celda."
  [^BufferedImage img bg [cx0 cy0 cx1 cy1]]
  (let [pts (for [y (range cy0 cy1 2) x (range cx0 cx1 2)
                  :when (opaque? img bg x y)]
              [x y])]
    [(apply min (map first pts)) (apply min (map second pts))
     (apply max (map first pts)) (apply max (map second pts))]))

(defn- make-sprites [src-dir]
  (let [src (read-image (io/file src-dir "personaje-1.jpeg"))
        bg (background-color src)
        sw (.getWidth src)
        sh (.getHeight src)
        cell-rect (fn [row col] [(quot (* col sw) 3) (quot (* row sh) 3)
                                 (quot (* (inc col) sw) 3) (quot (* (inc row) sh) 3)])
        boxes (into {} (for [row (range 3) col (range 3)]
                         [[row col] (bbox src bg (cell-rect row col))]))
        max-h (apply max (map (fn [[_ y0 _ y1]] (inc (- y1 y0))) (vals boxes)))
        max-w (apply max (map (fn [[x0 _ x1 _]] (inc (- x1 x0))) (vals boxes)))
        s (min (/ (double sprite-h) max-h) (/ (double sprite-h) max-w))
        out (BufferedImage. (* 3 frame) (* 3 frame) BufferedImage/TYPE_INT_ARGB)]
    (doseq [row (range 3)]
      ;; Caja común de la fila: centrado y pies estables entre cuadros.
      (let [rel (for [col (range 3)
                      :let [[x0 _ x1 y1] (boxes [row col])
                            [cx0 cy0] (cell-rect row col)]]
                  [(- x0 cx0) (- x1 cx0) (- y1 cy0)])
            ux-min (apply min (map first rel))
            ux-max (apply max (map second rel))
            uy-max (apply max (map #(nth % 2) rel))
            uw (* s (inc (- ux-max ux-min)))
            ox (/ (- frame uw) 2.0)]
        (doseq [col (range 3)]
          (let [[cx0 cy0 cx1 cy1] (cell-rect row col)]
            (dotimes [ty frame]
              (dotimes [tx frame]
                (let [sx (long (Math/floor (+ cx0 ux-min (/ (- (+ tx 0.5) ox) s))))
                      sy (long (Math/floor (- (+ cy0 uy-max 1) (/ (- feet-y (+ ty 0.5)) s))))]
                  (when (and (<= cx0 sx) (< sx cx1) (<= cy0 sy) (< sy cy1)
                             (< (+ ty 0.5) feet-y)
                             (opaque? src bg sx sy))
                    (.setRGB out (int (+ (* col frame) tx)) (int (+ (* row frame) ty))
                             (unchecked-int (bit-or 0xff000000
                                                    (bit-and (.getRGB src (int sx) (int sy)) 0xffffff))))))))))))
    (let [f (io/file assets-dir "player-sprites.png")]
      (write-png out f)
      (println "sprites →" (str f) (format "(escala %.4f, fondo %s)" s bg)))))

;; ---------------------------------------------------------------------------
;; Vista previa de la grilla (D2): para aprobar el trazado de INITIAL-GRID

(defn- make-preview []
  (let [grid @(requiring-resolve 'razti.shared.campus/INITIAL-GRID)
        spawns @(requiring-resolve 'razti.shared.campus/SPAWNS)
        base (read-image (io/file assets-dir "map-campus.jpeg"))
        out (BufferedImage. canvas canvas BufferedImage/TYPE_INT_ARGB)
        ^Graphics2D g (.createGraphics out)]
    (.setRenderingHint g RenderingHints/KEY_TEXT_ANTIALIASING RenderingHints/VALUE_TEXT_ANTIALIAS_ON)
    (.drawImage g base 0 0 nil)
    (doseq [y (range (count grid)) x (range (count (nth grid y)))]
      (let [v (get-in grid [y x])
            px (* x cell) py (* y cell)]
        (case (long v)
          1 (do (.setComposite g (AlphaComposite/getInstance AlphaComposite/SRC_OVER (float 0.35)))
                (.setColor g Color/RED)
                (.fillRect g px py cell cell))
          2 (do (.setComposite g (AlphaComposite/getInstance AlphaComposite/SRC_OVER (float 0.55)))
                (.setColor g (Color. 255 140 0))
                (.fillRect g (+ px 6) (+ py 6) (- cell 12) (- cell 12))
                (.setComposite g AlphaComposite/SrcOver)
                (.setColor g (Color. 120 60 0))
                (.drawRect g (+ px 6) (+ py 6) (- cell 13) (- cell 13)))
          nil)))
    (.setComposite g AlphaComposite/SrcOver)
    (.setColor g (Color. 255 255 0 160))
    (.setStroke g (BasicStroke. 1.0))
    (doseq [i (range 21)]
      (.drawLine g (* i cell) 0 (* i cell) canvas)
      (.drawLine g 0 (* i cell) canvas (* i cell)))
    (.setFont g (Font. Font/MONOSPACED Font/BOLD 11))
    (doseq [i (range 20)]
      (.setColor g Color/BLACK)
      (.fillRect g (* i cell) 0 14 12)
      (.fillRect g 0 (* i cell) 14 12)
      (.setColor g Color/YELLOW)
      (.drawString g (str i) (int (+ 1 (* i cell))) (int 10))
      (.drawString g (str i) (int 1) (int (+ 10 (* i cell)))))
    (.setFont g (Font. Font/SANS_SERIF Font/BOLD 20))
    (doseq [[i [x y]] (map-indexed vector spawns)]
      (let [px (* x cell) py (* y cell)]
        (.setColor g (Color. 0 255 255 200))
        (.setStroke g (BasicStroke. 3.0))
        (.drawRect g (+ px 2) (+ py 2) (- cell 5) (- cell 5))
        (.setColor g Color/BLACK)
        (.drawString g (str (inc i)) (int (+ px 14)) (int (+ py 28)))))
    (.dispose g)
    (let [f (io/file "target" "grid-preview.png")]
      (write-png out f)
      (println "preview →" (str f)))))

;; ---------------------------------------------------------------------------

(defn -main [& args]
  (let [[mode & more] (if (and (seq args) (not (.startsWith ^String (first args) "--")))
                        args
                        (cons "all" args))
        opts (apply hash-map more)
        src-dir (get opts "--src" "../1-files/img")]
    (case mode
      "map" (make-map src-dir)
      "sprites" (make-sprites src-dir)
      "preview" (make-preview)
      "all" (do (make-map src-dir) (make-sprites src-dir) (make-preview))
      (do (println "Modo desconocido:" mode "— usa all | map | sprites | preview")
          (System/exit 2)))))
