(ns deploy
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [core :refer [create-system]]
            [components.config :refer [fetch-config]]))
(def system (atom nil))

(println (fetch-config))

(defn reload-system []
  (if (nil? @system)
    (reset! system (component/start-system (create-system)))
    (do
      (log/info @system)
      (reset! system (-> @system
                         (component/stop-system)
                         (component/start-system))))))
(defn -main [& args]
  (println args)
  (reload-system))
