(ns modules.docker.controller
  (:require [compojure.core :refer [GET POST]]
            [ring.util.response :as response]
            [modules.docker.service :refer [start-all stop-all down-all get-containers stop-service down-service
                                            start-service update-compose get-compose get-service-log generate-docker-directory]]))

(defn prefixed [url] (str "/lumin/docker" url))

(defn routes []
  [(POST (prefixed "/environment") request
     (let [{:keys [sub]} (:authorized request)]
       (response/response (generate-docker-directory sub (:params request)))))
   (GET (prefixed "/:environment-name/start/all") request
     (let [{:keys [environment-name]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (start-all sub environment-name))))

   (GET (prefixed "/:environment-name/down/all") request
     (let [{:keys [environment-name]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (down-all sub environment-name))))

   (GET (prefixed "/:environment-name/stop/all") request
     (let [{:keys [environment-name]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (stop-all sub environment-name))))

   (GET (prefixed "/:environment-name/containers") request
     (let [{:keys [environment-name]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-containers sub environment-name))))

   (POST (prefixed "/:environment-name/service/stop") request
     (let [{:keys [environment-name]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (stop-service sub environment-name (:params request)))))

   (POST (prefixed "/:environment-name/service/start") request
     (let [{:keys [environment-name]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (start-service sub environment-name (:params request)))))

   (POST (prefixed "/:environment-name/service/down") request
     (let [{:keys [environment-name]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (down-service sub environment-name (:params request)))))

   (GET (prefixed "/:environment-name/service/logs") request
     (let [{:keys [environment-name]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-service-log sub environment-name (:params request)))))

   (POST (prefixed "/:environment-name/compose") request
     (let [{:keys [environment-name]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (update-compose sub environment-name (:params request)))))

   (GET (prefixed "/:environment-name/compose") request
     (let [{:keys [environment-name]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-compose sub environment-name))))])