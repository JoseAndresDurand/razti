(ns razti.server.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [org.httpkit.server :as http]
            [ring.middleware.session.memory :as memory]
            [razti.server.core :as core]
            [razti.server.ws-client :as c])
  (:import (java.net InetAddress InetSocketAddress NetworkInterface Socket)))

(def ^:private fake-sente
  {:ajax-get-or-ws-handshake-fn (constantly {:status 200 :body "chsk"})
   :ajax-post-fn (constantly {:status 200 :body "chsk"})})

(defn- handler [dev?]
  (core/app {:sente fake-sente :cfg {:dev? dev?} :store (memory/memory-store)}))

(defn- get-req [h uri]
  (h {:request-method :get :uri uri :headers {} :scheme :http :server-name "127.0.0.1" :server-port 3000}))

(deftest ^:S02 env-config
  (testing "Por defecto 127.0.0.1:3000 y orígenes locales"
    (let [cfg (core/env-config {})]
      (is (= "127.0.0.1" (:host cfg)))
      (is (= 3000 (:port cfg)))
      (is (false? (:dev? cfg)))
      (is (= #{"http://127.0.0.1:3000" "http://localhost:3000"} (:allowed-origins cfg)))))
  (testing "RAZTI_PORT inválido lanza"
    (is (thrown? clojure.lang.ExceptionInfo (core/env-config {"RAZTI_PORT" "70000"})))
    (is (thrown? clojure.lang.ExceptionInfo (core/env-config {"RAZTI_PORT" "abc"}))))
  (testing "Codespaces y orígenes extra (los inválidos se ignoran)"
    (let [cfg (core/env-config {"RAZTI_PORT" "3001"
                                "CODESPACE_NAME" "mi-espacio"
                                "GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN" "app.github.dev"
                                "RAZTI_ALLOWED_ORIGINS" "https://x.trycloudflare.com, javascript:alert(1)"}
                               "true")]
      (is (true? (:dev? cfg)))
      (is (contains? (:allowed-origins cfg) "http://127.0.0.1:3001"))
      (is (contains? (:allowed-origins cfg) "https://mi-espacio-3001.app.github.dev"))
      (is (contains? (:allowed-origins cfg) "https://x.trycloudflare.com"))
      (is (= 4 (count (:allowed-origins cfg)))))))

(deftest ^:S02 rutas-y-cabeceras
  (let [h (handler false)
        index (get-req h "/")
        set-cookie (str/join ";" (let [v (get-in index [:headers "Set-Cookie"])] (if (string? v) [v] v)))
        csp (get-in index [:headers "Content-Security-Policy"])]
    (testing "/health"
      (let [r (get-req h "/health")]
        (is (= 200 (:status r)))
        (is (= "ok" (:body r)))))
    (testing "/ con el token de la sesión y sin el marcador"
      (is (= 200 (:status index)))
      (is (not (str/includes? (:body index) "{{csrf-token}}")))
      (is (re-find #"name=\"csrf-token\" content=\"[A-Za-z0-9_-]{43}\"" (:body index)))
      (is (= "no-store" (get-in index [:headers "Cache-Control"]))))
    (testing "Cookie de sesión HttpOnly y SameSite=Strict"
      (is (str/includes? set-cookie "razti-session="))
      (is (str/includes? set-cookie "HttpOnly"))
      (is (str/includes? set-cookie "SameSite=Strict")))
    (testing "Cabeceras de seguridad (T19)"
      (is (not (str/includes? csp "unsafe-inline")))
      (is (not (str/includes? csp "unsafe-eval")))
      (is (not (str/includes? csp "9630")))
      (is (= "nosniff" (get-in index [:headers "X-Content-Type-Options"])))
      (is (= "same-origin" (get-in index [:headers "Referrer-Policy"]))))
    (testing "Ruta inexistente → 404"
      (is (= 404 (:status (get-req h "/no-existe")))))
    (testing "En desarrollo el CSP admite la recarga de shadow-cljs"
      (let [csp-dev (get-in (get-req (handler true) "/") [:headers "Content-Security-Policy"])]
        (is (str/includes? csp-dev "ws://127.0.0.1:9630"))))))

(defn free-port []
  (with-open [s (java.net.ServerSocket. 0 1 (InetAddress/getByName "127.0.0.1"))]
    (.getLocalPort s)))

(defn with-server
  "Arranca el servidor real en 127.0.0.1 con un puerto libre (orígenes de
  env-config para ese puerto), llama (f base-url port) y lo detiene."
  [f]
  (let [port (free-port)
        system (core/start! (core/env-config {"RAZTI_PORT" (str port)}))]
    (try (f (str "http://127.0.0.1:" port) port)
         (finally (core/stop! system)))))

(defn- non-loopback-addresses []
  (for [^NetworkInterface ni (enumeration-seq (NetworkInterface/getNetworkInterfaces))
        :when (.isUp ni)
        ^InetAddress a (enumeration-seq (.getInetAddresses ni))
        :when (and (not (.isLoopbackAddress a)) (not (.isLinkLocalAddress a)))]
    a))

(defn- connects? [^InetAddress addr port]
  (try (with-open [s (Socket.)]
         (.connect s (InetSocketAddress. addr (int port)) 500)
         true)
       (catch Exception _ false)))

(deftest ^:S02 escenario-S02-1-binding-por-defecto
  (testing "S02-1. Con la configuración por defecto, http-kit arranca en 127.0.0.1 (QA-04)"
    (let [captured (atom nil)
          run-server http/run-server
          port (free-port)]
      (with-redefs [http/run-server (fn [h opts] (reset! captured opts) (run-server h opts))]
        (let [system (core/start! (core/env-config {"RAZTI_PORT" (str port)}))]
          (core/stop! system)))
      (is (= "127.0.0.1" (:ip @captured)))
      (is (= port (:port @captured))))))

(deftest ^:S02 escenario-S02-1-salud-y-binding
  (with-server
    (fn [base port]
      (testing "S02-1. /health responde 200 ok"
        (is (= {:status 200 :body "ok"} (select-keys (c/http-get (str base "/health")) [:status :body]))))
      (testing "S02-1. Solo escucha en 127.0.0.1"
        (let [addrs (non-loopback-addresses)]
          (if (empty? addrs)
            (println "AVISO: sin interfaces no-loopback (sandbox); el binding se verifica con ss -tlnH")
            (doseq [a addrs]
              (is (not (connects? a port)) (str "responde en " a)))))))))

(deftest ^:S02 escenario-S02-2-origen-y-csrf
  (with-server
    (fn [base port]
      (let [{:keys [cookie token]} (c/fetch-session base)
            ws-url (fn [t] (str "ws://127.0.0.1:" port "/chsk?client-id=t1"
                                (when t (str "&csrf-token=" t))))]
        (testing "S02-2. Origin ajeno → 403"
          (is (= 403 (c/handshake-status (ws-url token) {"Cookie" cookie "Origin" "http://evil.example"}))))
        (testing "S02-2. Sin token CSRF → 403"
          (is (= 403 (c/handshake-status (ws-url nil) {"Cookie" cookie "Origin" base}))))
        (testing "Con Origin y token válidos → abre"
          (is (= :open (c/handshake-status (ws-url token) {"Cookie" cookie "Origin" base}))))))))

(deftest ^:S02 escenario-S02-3-eco
  (with-server
    (fn [base _]
      (let [client (c/connect! base)]
        (try
          (is (c/await-open client 10000))
          (is (= :ws (:type @(:state client))))
          (is (= {:t 42} (c/request! client [:razti/echo {:t 42}] 5000)))
          (finally (c/close! client)))))))
