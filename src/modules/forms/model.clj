(ns modules.forms.model
  (:require [utils.database :as database]))

(defn insert-form
  [ds authorised-id dto]
  (database/execute-one!
   ds
   {:insert-into [:form]
    :columns [:owner_id :project_id :fields :url_post :url_get :created_at]
    :values [[authorised-id (parse-long (:project-id dto)) (:fields dto) (:url-post dto) (:url-get dto) (System/currentTimeMillis)]]
    :returning :id}))

(defn get-form-by-id
  [ds id]
  (database/execute-one!
   ds
   {:select [:id, :owner_id, :project_id, :fields]
    :from [:form]
    :where [:= :id id]}))

(defn insert-data-by-form
  [ds form-id data]
  (database/execute-one!
   ds
   {:insert-into [:forms_data]
    :columns [:form_id :data :created_at]
    :values [[form-id data (System/currentTimeMillis)]]
    :returning :id}))

(defn get-all-data
  [ds id]
  (database/execute!
   ds
   {:select [:data]
    :from [:forms_data]
    :where [:= :form_id id]}))