(ns components.http-server.component
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [components.config :refer [fetch-config]]
            [components.http-server.router :refer [app-routes]]
            [components.http-server.middleware :as middlewares])
  (:import (org.eclipse.jetty.server Server)))

(defrecord HttpServerComponent
           [config datasource]
  component/Lifecycle

  (start [component]
    (log/info "Start HttpServer on :" (-> config :http-server :port))
    (let [server (jetty/run-jetty
                  (-> app-routes
                      (middlewares/wrap-exceptions-handling)
                      (middlewares/wrap-jwt-guard)
                      (middlewares/wrap-deps component)
                      (wrap-keyword-params)
                      (wrap-params)
                      (wrap-json-params)
                      (middlewares/wrap-request-logging)
                      (middlewares/wrap-content-type-json))
                  (:http-server config))]
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