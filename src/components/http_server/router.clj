(ns components.http-server.router
  (:require [compojure.core :refer [routes GET POST]]
            [ring.util.response :as response]
            [service.test-crud :refer [get-one get-all create]]))

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