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

(defn- describe-rejection
  "Datos de diagnóstico de un rechazo, sin tokens ni cookies."
  [req]
  (let [h (:headers req)]
    (str (name (or (:request-method req) :?)) " " (:uri req)
         " origin=" (pr-str (get h "origin"))
         " referer?=" (contains? h "referer")
         " host=" (pr-str (get h "host"))
         " x-forwarded-host=" (pr-str (get h "x-forwarded-host"))
         " sesión?=" (boolean (get-in req [:session :csrf-token]))
         " token-cliente?=" (boolean (or (get-in req [:params :csrf-token])
                                         (get h "x-csrf-token"))))))

(defn make-sente
  "Canal de Sente con Transit, orígenes permitidos y CSRF por defecto
  (lee [:session :csrf-token], T11; uid desde [:session :uid], T12).
  Cada rechazo se registra con su motivo (diagnóstico de proxies)."
  [cfg]
  (sente/make-channel-socket-server!
   (get-sch-adapter)
   {:packer (transit/get-packer)
    :allowed-origins (:allowed-origins cfg)
    :bad-origin-fn (fn [req]
                     (log :warn "rechazo por origen:" (describe-rejection req))
                     {:status 403 :body "Unauthorized origin"})
    :bad-csrf-fn (fn [req]
                   (log :warn "rechazo por CSRF:" (describe-rejection req))
                   {:status 403 :body "Bad CSRF token"})}))

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
