(ns modules.library.controller
  (:require [compojure.core :refer [GET]]
            [ring.util.response :as response]
            [modules.library.service :refer [get-all-projects get-all-widgets get-all-categories]]))

(defn prefixed [url] (str "/lumin/user/library" url))

(defn routes []
  [(GET (prefixed "/widgets") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-all-widgets datasource sub))))
   (GET (prefixed "/projects") request
     (let [{:keys [datasource]} (:deps request)
           {:keys [sub]} (:authorized request)]
       (response/response (get-all-projects datasource sub))))
   (GET (prefixed "/categories") request
     (let [{:keys [datasource]} (:deps request)]
       (response/response (get-all-categories datasource))))])
