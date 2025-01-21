(ns modules.docker.controller
  (:require [compojure.core :refer [GET POST]]
            [ring.util.response :as response]
            [modules.docker.service :refer [start-all stop-all down-all get-containers stop-container start-container
                                            update-compose get-compose get-container-log generate-docker-directory
                                            get-all-environments generate-docker-hidden get-configurations get-configuration-by-id
                                            create-env-by-server-made-configuration environment-upload]]))

(defn prefixed [url] (str "/lumin/docker" url))

(defn routes []
  [(POST (prefixed "/environment") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (generate-docker-directory datasource sub (:params request) false))))

   (GET (prefixed "/environment/hidden") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (generate-docker-hidden datasource sub))))

   (GET (prefixed "/environment") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-all-environments datasource sub))))

   (POST (prefixed "/:environment-id/:configuration-id/upload") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id configuration-id]} (:params request)
           {:keys [multipart-params]} request
           {:keys [sub]} (:authorized request)]
       (response/response (environment-upload datasource sub (parse-long environment-id) (parse-long configuration-id) multipart-params))))

   (POST (prefixed "/:environment-id/start/all") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (start-all datasource sub (parse-long environment-id)))))

   (POST (prefixed "/:environment-id/down/all") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (down-all datasource sub (parse-long environment-id)))))

   (POST (prefixed "/:environment-id/stop/all") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (stop-all datasource sub (parse-long environment-id)))))

   (GET (prefixed "/:environment-id/containers") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-containers datasource sub (parse-long environment-id)))))

   (POST (prefixed "/:environment-id/container/:container-id/stop") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id container-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (stop-container datasource sub (parse-long environment-id) (parse-long container-id)))))

   (POST (prefixed "/:environment-id/container/:container-id/start") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id container-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (start-container datasource sub (parse-long environment-id) (parse-long container-id)))))

   (POST (prefixed "/:environment-id/container/:container-id/logs") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id container-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-container-log datasource sub (parse-long environment-id) (parse-long container-id) (:params request)))))

   (POST (prefixed "/:environment-id/compose") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (update-compose datasource sub (parse-long environment-id) (:params request)))))

   (GET (prefixed "/:environment-id/compose") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-compose datasource sub (parse-long environment-id)))))

   (GET (prefixed "/:environment-id/compose") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-compose datasource sub (parse-long environment-id)))))

   (GET (prefixed "/configurations") request
     (let [{:keys [datasource]} (:deps request)]
       (response/response (get-configurations datasource))))

   (GET (prefixed "/configurations/:configuration-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [configuration-id]} (:params request)]
       (response/response (get-configuration-by-id datasource (parse-long configuration-id)))))

   (POST (prefixed "/configurations/:configuration-id/environment") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [configuration-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (create-env-by-server-made-configuration datasource sub (parse-long configuration-id) (:params request)))))])