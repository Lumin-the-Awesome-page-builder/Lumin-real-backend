(ns components.http-server
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [routes GET POST]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.util.response :as response]
            [service.test-crud :refer [get-one get-all create]]
            [components.config :refer [fetch-config]])
  (:import (org.eclipse.jetty.server Server)))

(def app-routes
  (routes
    (POST "/" request
      (let [{:keys [datasource]} (:deps request)
            {:keys [value]} (:params request)]
        (response/response (create datasource value))))
    (GET "/" request
      (let [{:keys [datasource]} (:deps request)]
        (response/response (get-all datasource))))
    (GET "/:id" request
      (let [{:keys [datasource]} (:deps request)
            {:keys [id]} (:params request)]
        (response/response (get-one datasource id))))))

(defn wrap-deps
  [handler deps]
  (fn [request]
    (handler (assoc request :deps deps))))



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