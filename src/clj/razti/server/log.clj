(ns razti.server.log
  "Registro mínimo del servidor: hora ISO, nivel y mensaje."
  (:require [clojure.string :as str])
  (:import (java.time Instant)))

(set! *warn-on-reflection* true)

(defn log [level & parts]
  (println (str (Instant/now)) (str/upper-case (name level)) (str/join " " parts)))
