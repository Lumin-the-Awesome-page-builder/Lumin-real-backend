(ns modules.nginx.controller
  (:refer-clojure :exclude [remove])
  (:require [compojure.core :refer [POST]]
            [ring.util.response :as response]
            [modules.nginx.service :refer [update-index deploy]]))

(defn prefixed [url] (str "/lumin/nginx" url))

(defn routes []
  [(POST (prefixed "/deploy/:project-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [project-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (deploy datasource sub project-id (:params request)))))
   (POST (prefixed "/upload/:project-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [project-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (update-index datasource sub (parse-long project-id) (:params request)))))])
