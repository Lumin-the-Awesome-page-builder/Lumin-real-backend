(ns modules.project.controller
  (:refer-clojure :exclude [remove])
  (:require [clojure.data.json :as json]
            [compojure.core :refer [GET PATCH POST DELETE]]
            [ring.util.response :as response]
            [modules.project.service :refer [get-by-id hide-shared-secret patch patch-preview create remove create-collaboration-link share]]
            [modules.editor.service :refer [edit]]))

(defn prefixed [url] (str "/lumin/project" url))

(defn routes []
  [(GET (prefixed "/:id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (->> (parse-long id)
            (get-by-id datasource sub)
            (hide-shared-secret)
            (json/write-str)
            (response/response))))
   (GET (prefixed "/:id/collaboration") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (create-collaboration-link datasource sub (parse-long id)))))
   (PATCH (prefixed "/:id/share") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (share datasource sub (parse-long id) (:params request)))))
   (GET (prefixed "/:id/start-edit") request
     (let [{:keys [datasource redis]} (:deps request)
           params (:params request)
           {:keys [sub]} (:authorized request)]
       (->> (edit redis datasource sub (-> params :id parse-long) params)
            (json/write-str)
            (response/response))))
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
