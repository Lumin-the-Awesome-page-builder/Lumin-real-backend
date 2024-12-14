(ns core
  (:require [com.stuartsierra.component :as component]
            [components.datasource :refer [datasource-component]]
            [components.ring-server.component :refer [create-server]]
            [components.redis :refer [create-redis]]))

(defn create-system
  []
  (component/system-map
   :datasource
   (datasource-component)

   :redis
   (component/using
    (create-redis)
    [:datasource])

   :http-server-component
   (component/using
    (create-server)
    [:datasource :redis])))
