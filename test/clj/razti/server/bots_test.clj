(ns razti.server.bots-test
  "Compuerta M2: 4 bots completan una partida por WebSocket (escenario S02-4)."
  (:require [clojure.test :refer [deftest is testing]]
            [razti.server.core-test :refer [with-server]]
            [razti.server.ws-client :as c]))

(defn- snapshot? [pred]
  (fn [[id data]] (and (= id :razti/snapshot) (pred data))))

(deftest ^:S02 escenario-S02-4-cuatro-bots
  (with-server
    (fn [base _]
      (let [bots (vec (repeatedly 4 #(c/connect! base)))]
        (try
          (doseq [b bots] (is (c/await-open b 10000)))
          (let [[b1 & others] bots
                uid (fn [b] (:uid @(:state b)))
                code (get-in (c/request! b1 [:razti/create-room {:name "B1"}] 5000) [:ok :code])]
            (testing "B1 crea la sala y B2–B4 se unen"
              (is (string? code))
              (doseq [[i b] (map-indexed vector others)]
                (is (= {:ok {:code code}}
                       (c/request! b [:razti/join-room {:code code :name (str "B" (+ i 2))}] 5000)))))
            (testing "B1 inicia; B2–B4 colocan una bomba sin moverse"
              (is (= {:ok {}} (c/request! b1 [:razti/start-game {}] 5000)))
              (doseq [b others] (c/send! b [:razti/input {:seq 1 :action :bomb}])))
            (testing "S02-4. En menos de 10 s la sala termina con B1 como ganador"
              (let [[_ final] (c/await-push b1 (snapshot? #(= :finished (:status %))) 10000)]
                (is (some? final))
                (is (= (uid b1) (:winner final)))
                (is (true? (get-in final [:players (uid b1) :alive?])))
                (doseq [b others]
                  (is (false? (get-in final [:players (uid b) :alive?]))))))
            (testing "B2–B4 recibieron snapshots con :tick > 0"
              (doseq [b others]
                (is (c/await-push b (snapshot? #(pos? (:tick %))) 2000)))))
          (finally
            (doseq [b bots] (c/close! b)))))))
  (testing "S02-4. El servidor se detiene sin hilos colgados (QA-05)"
    (is (not-any? #(and (= "razti-tick" (.getName ^Thread %)) (.isAlive ^Thread %))
                  (keys (Thread/getAllStackTraces))))))
