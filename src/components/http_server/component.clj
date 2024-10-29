(ns components.http-server.component
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [components.config :refer [fetch-config]]
            [components.http-server.router :refer [app-routes]]
            [components.http-server.middleware :refer [wrap-deps wrap-jwt-guard wrap-exceptions-handling wrap-request-logging]])
  (:import (org.eclipse.jetty.server Server)))

(defrecord HttpServerComponent
           [config datasource]
  component/Lifecycle

  (start [component]
    (log/info "Start HttpServer on :" (-> config :http-server :port))
    (let [server (jetty/run-jetty
                  (-> app-routes
                      (wrap-keyword-params)
                      (wrap-params)
                      (wrap-json-params)
                      (wrap-jwt-guard)
                      (wrap-exceptions-handling)
                      (wrap-request-logging)
                      (wrap-deps component))
                  (-> config :http-server))]
      (assoc component :server server)))

  (stop [component]
    (log/info "Stop HttpServer" component)
    (when-let [^Server server (:server component)]
      (log/info "Server found")
      (.stop server))
    (assoc component :server nil)))

(defn create-server
  []
  (map->HttpServerComponent {:config (fetch-config)}))