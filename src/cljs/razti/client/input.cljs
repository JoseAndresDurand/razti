(ns razti.client.input
  "Teclado → [:razti/input] con número de secuencia (TDD M2/S02 §4, C12)."
  (:require [razti.client.ws :as ws]))

(def ^:private key->action
  {"w" [:move :up] "arrowup" [:move :up]
   "s" [:move :down] "arrowdown" [:move :down]
   "a" [:move :left] "arrowleft" [:move :left]
   "d" [:move :right] "arrowright" [:move :right]
   " " [:bomb nil]})

(defonce ^:private seq-n (atom 0))

(defn- typing? [e]
  (contains? #{"INPUT" "TEXTAREA" "SELECT"} (some-> e .-target .-tagName)))

(defn- on-keydown [app e]
  (when (and (= :game (:screen @app)) (not (typing? e)))
    (let [k (.toLowerCase (.-key e))]
      (if (and (= k "g") ^boolean goog/DEBUG)
        (swap! app update :debug-grid? not)
        (when-let [[action dir] (key->action k)]
          (.preventDefault e)
          (when (= :playing (get-in @app [:snapshot :status]))
            (ws/send! [:razti/input (cond-> {:seq (swap! seq-n inc) :action action}
                                      dir (assoc :dir dir))])))))))

(defn install! [app]
  (.addEventListener js/window "keydown" #(on-keydown app %)))
