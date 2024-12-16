(ns components.redis
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clj-redis.client :as redis]
            [components.config :refer [fetch-config]]))

(defrecord RedisComponent [config]
  component/Lifecycle

  (start [component]
    (let [rds (redis/init config)]
      (log/info "Init reddis connection, (config " config ") " rds)
      (assoc component :redis rds)))

  (stop [component]
    (log/info "Stop redis server" (:redis component))
    (when-let [connection (:redis component)]
      (log/info "Found" connection)
      (redis/lease connection identity))
    (assoc component :redis nil)))

(defn create-redis []
  (map->RedisComponent {:config (:redis (fetch-config))}))
