(ns razti.shared.campus
  "Datos del mapa del campus (brief §4, C20, C27, C28). Solo datos.
  0 vacío · 1 hard-block (edificios, árboles, avenidas y muro) · 2 soft-block.
  Trazado PROVISIONAL (D2): aprobado por el líder el 2026-11-18 con target/grid-preview.png;
  el ajuste fino se hace en el S02 con el overlay del juego.")

;; Área jugable: columnas 2–17, filas 3–17. El resto es avenida o muro (C27).
;;                  0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19
(def INITIAL-GRID
  [[1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]   ; 0
   [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]   ; 1
   [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]   ; 2
   [1 1 1 1 1 1 1 0 1 1 1 2 0 0 0 1 1 1 1 1]   ; 3
   [1 1 1 1 1 1 1 2 0 0 1 1 1 0 0 1 1 1 1 1]   ; 4
   [1 1 1 0 0 1 1 0 2 2 1 1 1 2 0 1 1 1 1 1]   ; 5
   [1 1 0 0 0 0 1 0 0 1 1 1 1 1 0 1 1 1 1 1]   ; 6
   [1 1 1 1 0 2 0 0 1 1 1 1 1 0 2 1 1 1 1 1]   ; 7
   [1 1 1 1 1 0 0 2 1 1 0 0 2 0 0 2 0 1 1 1]   ; 8
   [1 1 1 1 1 0 0 0 1 0 0 0 1 1 1 0 2 1 1 1]   ; 9
   [1 1 1 1 1 1 0 0 0 2 0 2 0 0 0 0 0 1 1 1]   ; 10
   [1 1 1 1 1 1 0 2 0 0 1 0 2 1 0 2 0 1 1 1]   ; 11
   [1 1 1 1 1 1 0 0 2 0 1 0 0 1 0 0 0 1 1 1]   ; 12
   [1 1 1 1 1 1 2 1 1 0 1 1 0 1 2 0 2 0 1 1]   ; 13
   [1 1 1 1 1 1 0 1 1 0 1 1 1 1 0 2 0 2 1 1]   ; 14
   [1 1 1 1 1 1 0 1 1 2 1 1 1 1 0 0 0 0 1 1]   ; 15
   [1 1 1 1 1 1 0 1 1 0 1 1 1 1 2 0 0 0 1 1]   ; 16
   [1 1 1 1 1 1 0 1 0 0 1 1 1 1 0 0 0 0 1 1]   ; 17
   [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]   ; 18
   [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]]) ; 19

;; Apariciones (C20): noroeste, noreste, suroeste, sureste.
(def SPAWNS
  [[4 5]     ; NO: jardines junto a la vereda oeste
   [13 3]    ; NE: pasto al norte, junto a la vía interna
   [6 16]    ; SO: camino de tierra norte-sur
   [16 16]]) ; SE: terrenos de tierra al sur de la cancha
