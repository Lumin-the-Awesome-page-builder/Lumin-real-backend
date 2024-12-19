(ns modules.forms.controller
  (:refer-clojure :exclude [remove])
  (:require [compojure.core :refer [GET POST]]
            [ring.util.response :as response]
            [modules.forms.service :refer [create-form get-form-fields post-data get-data patch-form]]))

(defn prefixed [url] (str "/lumin/form" url))

(defn routes []
  [(POST (prefixed "/:project-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [project-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (create-form datasource sub (parse-long project-id) (:params request)))))

   (GET (prefixed "/:form-id/fields") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [form-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-form-fields datasource sub (parse-long form-id)))))

   (POST (prefixed "/:form-id/data") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [form-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (post-data datasource sub (parse-long form-id) (:params request)))))

   (GET (prefixed "/:form-id/data") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [form-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-data datasource sub (parse-long form-id)))))
   (POST (prefixed "/:project-id/update/:form-id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [form-id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (patch-form datasource sub (parse-long form-id) (:params request)))))])
