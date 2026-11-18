(ns razti.shared.specs-test
  (:require [clojure.test :refer [deftest is testing]]
            [razti.shared.specs :as specs]))

(deftest ^:S01 contratos-de-entrada
  (testing "Input (§5): :move, :bomb y :leave válidos; malformados rechazados"
    (is (specs/valid-input? {:player-id "p1" :action :move :dir :up :seq 3}))
    (is (specs/valid-input? {:player-id "p1" :action :bomb}))
    (is (specs/valid-input? {:player-id "p1" :action :leave}))
    (is (not (specs/valid-input? {:player-id "p1" :action :move})))
    (is (not (specs/valid-input? {:player-id "p1" :action :move :dir :diagonal})))
    (is (not (specs/valid-input? {:player-id "p1" :action :jump})))
    (is (not (specs/valid-input? {:player-id "" :action :bomb})))
    (is (not (specs/valid-input? {:player-id "p1" :action :bomb :seq -1})))))

(deftest ^:S01 contratos-de-eventos-del-cliente
  (testing "ClientEvent (§5): eventos válidos"
    (is (specs/valid-client-event? [:razti/create-room {:name "Pepe_1"}]))
    (is (specs/valid-client-event? [:razti/join-room {:code "UNSA2" :name "Ana"}]))
    (is (specs/valid-client-event? [:razti/start-game {}]))
    (is (specs/valid-client-event? [:razti/restart {}]))
    (is (specs/valid-client-event? [:razti/input {:seq 1 :action :move :dir :left}])))
  (testing "Código de sala: 5 caracteres del alfabeto sin 0/O/1/I"
    (doseq [bad ["UNSA0" "UNSA1" "UNSAO" "unsa2" "ABCD" "ABCDEF"]]
      (is (not (specs/valid-client-event? [:razti/join-room {:code bad :name "Ana"}])) bad)))
  (testing "Nickname: 1 a 12 caracteres [A-Za-z0-9_-]"
    (doseq [bad ["" "abcdefghijklm" "con espacio" "ñandú" "<b>"]]
      (is (not (specs/valid-client-event? [:razti/create-room {:name bad}])) bad)))
  (testing "SEC-01: un salto de línea final se rechaza en JVM y Node"
    (is (not (specs/valid-client-event? [:razti/create-room {:name "abc\n"}])))
    (is (not (specs/valid-client-event? [:razti/join-room {:code "UNSA2\n" :name "Ana"}]))))
  (testing "Acción del cliente: :leave no es una entrada del cliente; :seq acotado"
    (is (not (specs/valid-client-event? [:razti/input {:seq 1 :action :leave}])))
    (is (not (specs/valid-client-event? [:razti/input {:action :bomb}])))
    (is (not (specs/valid-client-event? [:razti/input {:seq 2147483648 :action :bomb}])))
    (is (not (specs/valid-client-event? [:razti/unknown {}])))))

(deftest ^:S02 frontera-cerrada
  (testing "S02: eventos nuevos válidos"
    (is (specs/valid-client-event? [:razti/leave-room {}]))
    (is (specs/valid-client-event? [:razti/echo {:t 1}]))
    (is (specs/valid-client-event? [:razti/sync {}]))
    (is (not (specs/valid-client-event? [:razti/sync {:code "UNSA2"}]))))
  (testing "SEC-03: mapas cerrados, sin claves extra (ni :player-id del cliente)"
    (is (not (specs/valid-client-event? [:razti/create-room {:name "Ana" :player-id "p9"}])))
    (is (not (specs/valid-client-event? [:razti/input {:seq 1 :action :bomb :player-id "p9"}])))
    (is (not (specs/valid-client-event? [:razti/start-game {:x 1}]))))
  (testing "SEC-04: entradas que no son eventos devuelven false sin lanzar"
    (doseq [bad [5 nil "x" {} [] [:razti/echo] [:razti/echo {:t 1} :extra] ["razti/echo" {:t 1}]]]
      (is (false? (specs/valid-client-event? bad)) (pr-str bad)))))
