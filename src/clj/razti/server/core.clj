(ns razti.server.core
  "Servidor de RAZTI: configuración, rutas, sesión, CSRF y cabeceras
  (TDD M2/S02 §4; núcleo T11, T12, T19)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [org.httpkit.server :as http]
            [reitit.ring :as ring]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.memory :as memory]
            [razti.server.ws :as ws])
  (:import (java.security SecureRandom)
           (java.util Base64 UUID))
  (:gen-class))

(set! *warn-on-reflection* true)

;; ---------------------------------------------------------------------------
;; Configuración (pura)

(def ^:private origin-re #"^https?://[A-Za-z0-9.-]+(:\d{1,5})?$")

(defn- parse-port [s]
  (let [p (if (str/blank? s) 3000 (parse-long (str/trim s)))]
    (if (and p (<= 1 p 65535))
      p
      (throw (ex-info (str "RAZTI_PORT inválido: " (pr-str s)) {:value s})))))

(defn env-config
  "env: mapa String→String. dev-prop: valor de la propiedad razti.dev."
  ([env] (env-config env nil))
  ([env dev-prop]
   (let [host (let [h (get env "RAZTI_HOST")] (if (str/blank? h) "127.0.0.1" (str/trim h)))
         port (parse-port (get env "RAZTI_PORT"))
         codespace (let [n (get env "CODESPACE_NAME")
                         d (get env "GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN")]
                     (when (and (not (str/blank? n)) (not (str/blank? d)))
                       (str "https://" n "-" port "." d)))
         extra (->> (str/split (or (get env "RAZTI_ALLOWED_ORIGINS") "") #",")
                    (map str/trim)
                    (remove str/blank?))
         valid-extra (filter #(re-matches origin-re %) extra)]
     (doseq [o (remove #(re-matches origin-re %) extra)]
       (ws/log :warn "origen ignorado en RAZTI_ALLOWED_ORIGINS:" (pr-str o)))
     {:host host
      :port port
      :dev? (= "true" dev-prop)
      :allowed-origins (cond-> (into #{(str "http://127.0.0.1:" port)
                                       (str "http://localhost:" port)}
                                     valid-extra)
                         codespace (conj codespace))})))

;; ---------------------------------------------------------------------------
;; HTTP

(defonce ^:private ^SecureRandom rng (SecureRandom.))

(defn- random-token []
  (let [b (byte-array 32)]
    (.nextBytes rng b)
    (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) b)))

(defn content-security-policy
  "T19. En desarrollo admite el websocket de recarga y el eval de shadow-cljs."
  [dev?]
  (str "default-src 'self'; "
       "script-src 'self'" (when dev? " 'unsafe-eval'") "; "
       "style-src 'self'; img-src 'self' data:; "
       "connect-src 'self'" (when dev? " ws://127.0.0.1:9630 ws://localhost:9630") "; "
       "frame-ancestors 'none'; base-uri 'none'; form-action 'self'"))

(defn- wrap-security-headers [handler dev?]
  (let [headers {"Content-Security-Policy" (content-security-policy dev?)
                 "X-Content-Type-Options" "nosniff"
                 "Referrer-Policy" "same-origin"}]
    (fn [req]
      (let [resp (handler req)]
        (if (map? resp)
          (update resp :headers #(merge headers %))
          resp)))))

(defn- index-handler
  "Sirve index.html con el token CSRF de la sesión; crea la sesión si falta (T11, T12)."
  [template]
  (fn [req]
    (let [s (or (:session req) {})
          s' (cond-> s
               (not (:uid s)) (assoc :uid (str (UUID/randomUUID)))
               (not (:csrf-token s)) (assoc :csrf-token (random-token)))]
      (cond-> {:status 200
               :headers {"Content-Type" "text/html; charset=utf-8"
                         "Cache-Control" "no-store"}
               :body (str/replace template "{{csrf-token}}" (:csrf-token s'))}
        (not= s s') (assoc :session s')))))

(defn- health [_]
  {:status 200 :headers {"Content-Type" "text/plain; charset=utf-8"} :body "ok"})

(defn app [{:keys [sente cfg store]}]
  (let [template (slurp (or (io/resource "public/index.html")
                            (throw (ex-info "Falta public/index.html" {}))))
        router (ring/router
                [["/" {:get (index-handler template)}]
                 ["/health" {:get health}]
                 ["/chsk" {:get (:ajax-get-or-ws-handshake-fn sente)
                           :post (:ajax-post-fn sente)}]])]
    (-> (ring/ring-handler router
                           (ring/routes (ring/create-resource-handler {:path "/"})
                                        (ring/create-default-handler)))
        wrap-keyword-params
        wrap-params
        (wrap-session {:store store
                       :cookie-name "razti-session"
                       :cookie-attrs {:http-only true :same-site :strict}})
        (wrap-security-headers (:dev? cfg)))))

;; ---------------------------------------------------------------------------
;; Ciclo de vida

(defn start!
  "Arranca el sistema. Con :port 0 usa un puerto libre (pruebas)."
  [cfg]
  (let [system {:cfg cfg
                :store (memory/memory-store)
                :sente (ws/make-sente cfg)}
        system (assoc system :stop-router (ws/start-router! system))
        server (http/run-server (app system) {:ip (:host cfg)
                                              :port (:port cfg)
                                              :legacy-return-value? false})
        port (http/server-port server)]
    (ws/log :info (str "RAZTI escuchando en http://" (:host cfg) ":" port))
    (assoc system :server server :port port)))

(defn stop! [{:keys [server stop-router]}]
  (when stop-router (stop-router))
  (when server
    (when-let [p (http/server-stop! server {:timeout 100})]
      (deref p 5000 nil))))

(defn -main [& _]
  (let [system (start! (env-config (into {} (System/getenv))
                                   (System/getProperty "razti.dev")))]
    (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable (fn [] (stop! system))))
    @(promise)))
