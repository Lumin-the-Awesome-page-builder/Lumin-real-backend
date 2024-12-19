(ns modules.nginx.controller
  (:refer-clojure :exclude [remove])
  (:require [compojure.core :refer [GET POST]]
            [ring.util.response :as response]
            [modules.nginx.service :refer [update-index create-nginx-directory reload-nginx]]))

(defn prefixed [url] (str "/lumin/nginx" url))

(defn routes []
  [(POST (prefixed "/:project-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [project-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (create-nginx-directory datasource sub (parse-long project-id) (:params request)))))

   (POST (prefixed "/index/:project-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [project-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (update-index datasource sub (parse-long project-id) (:params request)))))
   (GET (prefixed "/reload/:project-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [project-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (reload-nginx datasource sub (parse-long project-id)))))])
