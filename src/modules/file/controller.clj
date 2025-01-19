(ns modules.file.controller
  (:require [compojure.core :refer [GET]]
            [ring.util.response :as response]
            [utils.file :as f]))

(defn prefixed [url] (str "/lumin/file" url))

(defn routes []
  [(GET (prefixed "/:file-name") request
     (let [{:keys [file-name]} (:params request)]
       (-> (response/response (f/get-stream file-name)))))])
