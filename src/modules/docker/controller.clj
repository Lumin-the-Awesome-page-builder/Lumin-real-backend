(ns modules.docker.controller
  (:require [compojure.core :refer [GET POST]]
            [ring.util.response :as response]
            [modules.docker.service :refer [start-all stop-all down-all get-containers stop-container start-container
                                            update-compose get-compose get-container-log generate-docker-directory
                                            get-all-environments]]))

(defn prefixed [url] (str "/lumin/docker" url))

(defn routes []
  [(POST (prefixed "/environment") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (generate-docker-directory datasource sub (:params request)))))

   (GET (prefixed "/environment") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-all-environments datasource sub))))

   (GET (prefixed "/:environment-id/start/all") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (start-all datasource sub (parse-long environment-id)))))

   (GET (prefixed "/:environment-id/down/all") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (down-all datasource sub (parse-long environment-id)))))

   (GET (prefixed "/:environment-id/stop/all") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (stop-all datasource sub (parse-long environment-id)))))

   (GET (prefixed "/:environment-id/containers") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-containers datasource sub (parse-long environment-id)))))

   (POST (prefixed "/:environment-id/container/stop") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (stop-container datasource sub (parse-long environment-id) (:params request)))))

   (POST (prefixed "/:environment-id/container/start") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (start-container datasource sub (parse-long environment-id) (:params request)))))

   (POST (prefixed "/:environment-id/container/logs") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-container-log datasource sub (parse-long environment-id) (:params request)))))

   (POST (prefixed "/:environment-id/compose") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (update-compose datasource sub (parse-long environment-id) (:params request)))))

   (GET (prefixed "/:environment-id/compose") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [environment-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-compose datasource sub (parse-long environment-id)))))])