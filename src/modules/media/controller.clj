(ns modules.media.controller
  (:require [compojure.core :refer [GET POST DELETE]]
            [ring.util.response :as response]
            [modules.media.service :as service]))

(defn prefixed [url] (str "/lumin/media" url))

(defn routes []
  [(GET (prefixed "") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (service/get-user-media datasource sub))))
   (POST (prefixed "") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (service/upload-media datasource sub (:params request)))))
   (POST (prefixed "/attach/:project-id/:media-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [project-id media-id]} (:params request)]
       (response/response (service/attach-to-project datasource (parse-long project-id) (parse-long media-id)))))
   (DELETE (prefixed "/attach/:project-id/:media-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [project-id media-id]} (:params request)]
       (response/response (service/remove-from-project datasource (parse-long project-id) (parse-long media-id)))))
   (GET (prefixed "/attached/:project-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [project-id]} (:params request)]
       (response/response (service/get-attached datasource (parse-long project-id)))))])