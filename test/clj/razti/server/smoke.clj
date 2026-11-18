(ns razti.server.smoke
  "Comprobación de humo contra un servidor en marcha: /health y eco por
  WebSocket. Uso: clojure -M:smoke [url-base] (por defecto http://127.0.0.1:3000)."
  (:require [razti.server.ws-client :as c]))

(defn -main [& [base-url]]
  (let [base (or base-url "http://127.0.0.1:3000")
        code (try
               (let [{:keys [status body]} (c/http-get (str base "/health"))]
                 (println "health:" status body)
                 (if (and (= 200 status) (= "ok" body))
                   (let [client (c/connect! base)]
                     (if (c/await-open client 10000)
                       (let [reply (c/request! client [:razti/echo {:t 42}] 5000)]
                         (println "eco por WebSocket:" (pr-str reply))
                         (c/close! client)
                         (if (= {:t 42} reply) 0 1))
                       (do (println "el WebSocket no abrió en 10 s") 1)))
                   1))
               (catch Exception e
                 (println "fallo:" (.getMessage e))
                 1))]
    (println (if (zero? code) "SMOKE OK" "SMOKE FALLÓ"))
    (shutdown-agents)
    (System/exit code)))
