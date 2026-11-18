(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def uber-file "target/razti.jar")
(def main-ns 'razti.server.core)

(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path class-dir}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src/clj" "src/cljc" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :ns-compile [main-ns]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main main-ns}))
