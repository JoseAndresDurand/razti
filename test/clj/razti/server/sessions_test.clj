(ns razti.server.sessions-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.middleware.session.store :as store]
            [razti.server.sessions :as sessions]))

(deftest ^:S02 almacen-de-sesiones-acotado
  (let [now (atom 0)
        s (sessions/bounded-store {:max-entries 3 :ttl-ms 1000 :now-fn #(deref now)})]
    (testing "SEC-01. Tope: al pasar de 3 sesiones se expulsa la más antigua"
      (let [k1 (store/write-session s nil {:uid "a"})
            _ (swap! now inc)
            k2 (store/write-session s nil {:uid "b"})
            _ (swap! now inc)
            k3 (store/write-session s nil {:uid "c"})
            _ (swap! now inc)
            k4 (store/write-session s nil {:uid "d"})]
        (is (= 3 (sessions/size s)))
        (is (nil? (store/read-session s k1)))
        (is (= {:uid "b"} (store/read-session s k2)))
        (is (= {:uid "d"} (store/read-session s k4)))
        (testing "SEC-01. Caducidad: una sesión sin uso durante ttl-ms desaparece"
          (reset! now 2000)
          (is (nil? (store/read-session s k3))))
        (testing "SEC-01. purge! elimina las caducadas"
          (reset! now 5000)
          (is (zero? (sessions/purge! s 5000))))))
    (testing "La lectura renueva la sesión; delete la quita"
      (reset! now 10000)
      (let [k (store/write-session s nil {:uid "e"})]
        (reset! now 10900)
        (is (= {:uid "e"} (store/read-session s k)))
        (reset! now 11800)
        (is (= {:uid "e"} (store/read-session s k)))
        (store/delete-session s k)
        (is (nil? (store/read-session s k)))))))
