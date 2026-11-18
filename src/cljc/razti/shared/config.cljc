(ns razti.shared.config
  "Constantes del juego. Todo tiempo se mide en ticks (C4, C5).")

(def tick-rate-hz 20)
(def tick-ms 50)

(def grid-cols 20)
(def grid-rows 20)
(def cell-px 40)
(def canvas-px 800)

(def max-players 4)
(def min-players-to-start 2)

(def move-cooldown-ticks 4)   ; R2: 1 celda cada 200 ms
(def max-bombs-per-player 1)  ; R3
(def bomb-fuse-ticks 50)      ; R4: 2500 ms
(def bomb-radius 2)           ; §5
(def blast-ticks 10)          ; R5: 500 ms

(def room-code-length 5)
(def room-code-alphabet "ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
(def nickname-max-length 12)
(def max-inputs-per-second 30) ; lo aplica el servidor (S02)

;; Servidor (S02; conflicto #5 y T15, T20)
(def max-rooms 3)
(def room-idle-ms 300000)  ; 5 min sin actividad
(def sweep-ms 10000)       ; barrido de salas
(def player-colors ["#2F6FD6" "#D64545" "#3FA34D" "#E0B92E"]) ; por :player/join-order
