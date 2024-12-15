(ns modules.docker.controller
  (:require [compojure.core :refer [GET, POST]]
            [ring.util.response :as response]
            [modules.docker.service :refer [generate-docker-directory start-all stop-all down-all]]))

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
       (response/response (stop-all datasource sub (parse-long id)))))])
