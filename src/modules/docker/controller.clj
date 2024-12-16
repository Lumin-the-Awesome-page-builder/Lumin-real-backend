(ns modules.docker.controller
  (:require [compojure.core :refer [GET POST]]
            [ring.util.response :as response]
            [modules.docker.service :refer [start-all stop-all down-all get-containers stop-service down-service
                                            start-service update-compose get-compose get-service-log]]))

(defn prefixed [url] (str "/lumin/docker" url))

(defn routes []
  [(GET (prefixed "/:id/start/all") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (start-all datasource sub (parse-long id)))))

   (GET (prefixed "/:id/down/all") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (down-all datasource sub (parse-long id)))))

   (GET (prefixed "/:id/stop/all") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (stop-all datasource sub (parse-long id)))))

   (GET (prefixed "/:id/containers") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-containers datasource sub (parse-long id)))))

   (POST (prefixed "/:id/service/stop") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (stop-service datasource sub (parse-long id) (:params request)))))

   (POST (prefixed "/:id/service/start") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (start-service datasource sub (parse-long id) (:params request)))))

   (POST (prefixed "/:id/service/down") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (down-service datasource sub (parse-long id) (:params request)))))

   (GET (prefixed "/:id/service/logs") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-service-log datasource sub (parse-long id) (:params request)))))

   (POST (prefixed "/:id/compose") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (update-compose datasource sub (parse-long id) (:params request)))))

   (GET (prefixed "/:id/compose") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-compose datasource sub (parse-long id)))))])