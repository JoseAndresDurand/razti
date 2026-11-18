(ns razti.server.ws
  "Sente (WebSocket), router de eventos y envío a salas (TDD M2/S02 §4)."
  (:require [clojure.string :as str]
            [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as transit]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [razti.shared.specs :as specs])
  (:import (java.time Instant)))

(set! *warn-on-reflection* true)

(defn log
  "Registro mínimo del servidor: hora ISO, nivel y mensaje."
  [level & parts]
  (println (str (Instant/now)) (str/upper-case (name level)) (str/join " " parts)))

(defn make-sente
  "Canal de Sente con Transit, orígenes permitidos y CSRF por defecto
  (lee [:session :csrf-token], T11; uid desde [:session :uid], T12)."
  [cfg]
  (sente/make-channel-socket-server!
   (get-sch-adapter)
   {:packer (transit/get-packer)
    :allowed-origins (:allowed-origins cfg)}))

(defn- reply! [?reply-fn value]
  (when ?reply-fn (?reply-fn value)))

(defn handle-event!
  "Router de eventos. Los :chsk/* internos van primero; el resto se valida
  con Malli antes de tocar el estado."
  [_system {:keys [id ?data ?reply-fn event uid]}]
  (cond
    (= "chsk" (namespace id)) nil

    (not (specs/valid-client-event? event))
    (log :warn "evento inválido descartado" (pr-str id) "uid" (pr-str uid))

    :else
    (case id
      :razti/echo (reply! ?reply-fn ?data)
      (log :warn "evento sin manejador" (pr-str id)))))

(defn start-router!
  "Arranca el router de Sente. Devuelve la función de parada."
  [system]
  (sente/start-server-chsk-router!
   (get-in system [:sente :ch-recv])
   (fn [ev-msg]
     (try
       (handle-event! system ev-msg)
       (catch Throwable t
         (log :error "fallo en el router:" (.getMessage t)))))))
