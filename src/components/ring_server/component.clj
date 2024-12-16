(ns components.ring-server.component
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty9 :refer [run-jetty]]
            [components.config :refer [fetch-config]]
            [components.ring-server.router :refer [app-routes]])
  (:import (org.eclipse.jetty.server Server)))

(defrecord RingServerComponent
           [config datasource]
  component/Lifecycle

  (start [component]
    (log/info "Start HttpServer on :" (-> config :http-server :port))
    (let [server (run-jetty (app-routes component) (:http-server config))]
      (assoc component :server server)))

  (stop [component]
    (log/info "Stopping HttpServer..." component)
    (when-let [^Server server (:server component)]
      (log/info "Server found")
      (.stop server))
    (assoc component :server nil)))

(defn create-server
  []
  (map->RingServerComponent {:config (fetch-config)}))