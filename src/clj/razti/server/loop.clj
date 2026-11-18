(ns razti.server.loop
  "Reloj de 20 Hz por sala y snapshots (TDD M2/S02 §4; núcleo T13, T14)."
  (:require [razti.shared.config :as config]
            [razti.shared.engine :as engine]
            [razti.server.log :refer [log]]
            [razti.server.rooms :as rooms])
  (:import (java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit)))

(set! *warn-on-reflection* true)

(defn new-executor
  "Pool programado de 2 hilos daemon (relojes de sala y barrido)."
  ^ScheduledExecutorService []
  (Executors/newScheduledThreadPool
   2
   (reify ThreadFactory
     (newThread [_ r]
       (doto (Thread. r "razti-tick") (.setDaemon true))))))

(defn snapshot
  "Pura. Estado para el cliente; la grilla solo si cambió su versión (C11, T14)."
  [room last-grid-version]
  (let [gv (:room/grid-version room)]
    (cond-> {:tick (:room/tick room)
             :status (:room/status room)
             :winner (:room/winner room)
             :grid-version gv
             :players (into {}
                            (map (fn [[id p]]
                                   [id {:name (:player/name p)
                                        :pos (:player/pos p)
                                        :dir (:player/dir p)
                                        :alive? (:player/alive? p)
                                        :join-order (:player/join-order p)
                                        :last-seq (:player/last-seq p)}]))
                            (:room/players room))
             :bombs (mapv (fn [b] {:pos (:bomb/pos b) :ticks-left (:bomb/ticks-left b)})
                          (:room/bombs room))
             :blasts (mapv (fn [b] {:cells (:blast/cells b)}) (:room/blasts room))}
      (not= gv last-grid-version) (assoc :grid (:room/grid room)))))

(defn- tick!
  "Un tick de la sala. Si la sala ya no existe o no está en :playing, la tarea
  se retira a sí misma (QA-01, QA-02). Si la partida termina en este tick, el
  reloj sigue un tick más: así un reinicio inmediato lo reutiliza."
  [system code last-gv self]
  (let [st (:rooms system)
        ra (rooms/room-atom st code)]
    (if (or (nil? ra) (not= :playing (:room/status @ra)))
      (rooms/clear-clock-if! st code @self)
      (let [room (swap! ra engine/tick-game)
            s (snapshot room @last-gv)]
        (vreset! last-gv (:room/grid-version room))
        (rooms/send-room! system room [:razti/snapshot s])
        (when (= :finished (:room/status room))
          (rooms/send-room! system room [:razti/room (rooms/view room)]))))))

(defn start-clock!
  "Tarea de tasa fija (config/tick-ms) para la sala; idempotente. Sin
  :executor en el sistema (pruebas del router) no hace nada."
  [system code]
  (when-let [^ScheduledExecutorService ex (:executor system)]
    ;; Un reloj ya cancelado o terminado cuenta como ausente.
    (when-let [^java.util.concurrent.Future old (rooms/clock (:rooms system) code)]
      (when (.isDone old) (rooms/clear-clock-if! (:rooms system) code old)))
    (when-not (rooms/clock (:rooms system) code)
      (let [last-gv (volatile! -1)
            self (promise)
            task (fn []
                   (try
                     (tick! system code last-gv self)
                     (catch Throwable t
                       (log :error "fallo en el tick de" code ":" (.getMessage t)))))
            f (.scheduleAtFixedRate ex ^Runnable task
                                    (long config/tick-ms) (long config/tick-ms) TimeUnit/MILLISECONDS)]
        (deliver self f)
        (rooms/set-clock! (:rooms system) code f)))))
