(ns modules.widget.controller
  (:refer-clojure :exclude [remove])
  (:require [compojure.core :refer [GET PATCH POST DELETE]]
            [ring.util.response :as response]
            [modules.widget.service :refer [get-by-id patch create remove]]))

(defn prefixed [url] (str "/widget" url))

(defn routes []
  [(GET (prefixed "/:id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-by-id datasource sub (parse-long id)))))
   (PATCH (prefixed "/:id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (patch datasource sub (parse-long id) (:params request)))))
   (DELETE (prefixed "/:id") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [id]} (:params request)
           {:keys [sub]} (:authorized request)]
       (response/response (remove datasource sub (parse-long id)))))
   (POST (prefixed "") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (create datasource sub (:params request)))))])
