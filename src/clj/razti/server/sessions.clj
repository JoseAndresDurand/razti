(ns razti.server.sessions
  "Almacén de sesiones en memoria con caducidad y tope (auditoría S02, SEC-01):
  sustituye a ring.middleware.session.memory, que crece sin límite."
  (:require [ring.middleware.session.store :as store])
  (:import (java.util UUID)))

(set! *warn-on-reflection* true)

(defn- expired? [entry now ttl-ms]
  (>= (- now (:t entry)) ttl-ms))

(defn- evict-oldest [m max-entries]
  (if (> (count m) max-entries)
    (dissoc m (key (apply min-key (comp :t val) m)))
    m))

(deftype BoundedStore [data max-entries ttl-ms now-fn]
  store/SessionStore
  (read-session [_ k]
    (let [now (now-fn)]
      (when-let [entry (get @data k)]
        (if (expired? entry now ttl-ms)
          (do (swap! data dissoc k) nil)
          (do (swap! data (fn [m] (if (contains? m k) (assoc-in m [k :t] now) m)))
              (:v entry))))))
  (write-session [_ k v]
    (let [k (or k (str (UUID/randomUUID)))]
      (swap! data (fn [m] (evict-oldest (assoc m k {:v v :t (now-fn)}) max-entries)))
      k))
  (delete-session [_ k]
    (swap! data dissoc k)
    nil))

(defn bounded-store
  "Opciones: :max-entries, :ttl-ms, :now-fn (inyectable en pruebas)."
  [{:keys [max-entries ttl-ms now-fn]}]
  (BoundedStore. (atom {}) max-entries ttl-ms now-fn))

(defn size [^BoundedStore s] (count @(.-data s)))

(defn purge!
  "Elimina las sesiones caducadas. → cuántas quedan."
  [^BoundedStore s now]
  (let [ttl (.-ttl-ms s)]
    (count (swap! (.-data s) (fn [m] (into {} (remove (fn [[_ e]] (expired? e now ttl))) m))))))
