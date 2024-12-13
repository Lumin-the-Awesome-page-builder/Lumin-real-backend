(ns modules.project.controller
  (:refer-clojure :exclude [remove])
  (:require [compojure.core :refer [GET PATCH POST DELETE]]
            [ring.util.response :as response]
            [modules.project.service :refer [get-by-id patch patch-preview create remove create-collaboration-link share]]))

(defn prefixed [url] (str "/lumin/project" url))

(defn routes []
  [(GET (prefixed "/:id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-by-id datasource sub (parse-long id)))))
   (GET (prefixed "/:id/collaboration") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (create-collaboration-link datasource sub id))))
   (GET (prefixed "/:id/share") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (share datasource sub id (:params request)))))
   (PATCH (prefixed "/:id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (patch datasource sub (parse-long id) (:params request)))))
   (PATCH (prefixed "/:id/preview") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (patch-preview datasource sub (parse-long id) (-> request :params :preview)))))
   (DELETE (prefixed "/:id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (remove datasource sub (parse-long id)))))
   (POST (prefixed "") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (create datasource sub (:params request)))))])
