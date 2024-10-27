(ns core
  (:require [com.stuartsierra.component :as component]
            [components.datasource :refer [datasource-component]]
            [components.http-server :refer [create-server]]))

(defn create-system
  []
  (component/system-map
   :datasource (datasource-component)

   :http-server-component
   (component/using
    (create-server)
    [:datasource])))
