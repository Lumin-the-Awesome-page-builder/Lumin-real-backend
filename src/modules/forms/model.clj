(ns modules.forms.model
  (:require [utils.database :as database]))

(defn insert-form
  [ds authorised-id dto]
  (database/execute-one!
   ds
   {:insert-into [:form]
    :columns [:owner_id :project_id :fields :url_post :url_get :created_at :name]
    :values [[authorised-id (parse-long (:project-id dto)) (:fields dto) (:url-post dto) (:url-get dto) (System/currentTimeMillis) (:name dto)]]
    :returning :id}))

(defn get-form-by-id
  [ds id]
  (database/execute-one!
   ds
   {:select [:id, :owner_id, :project_id, :fields, :name, :url_post, :url_get]
    :from [:form]
    :where [:= :id id]}))

(defn get-forms-by-project
  [ds project-id]
  (database/execute!
    ds
    {:select [:id :name]
     :from [:form]
     :where [:= :project_id project-id]}))

(defn insert-data-by-form
  [ds form-id data]
  (database/execute-one!
   ds
   {:insert-into [:forms_data]
    :columns [:form_id :data :created_at]
    :values [[form-id data (System/currentTimeMillis)]]
    :returning :id}))

(defn update-name-dto
  [dto form]
  (if (= nil (:name dto))
    (assoc dto :name (:name form))
    dto))

(defn update-fields-dto
  [dto form]
  (if (= nil (:fields dto))
    (assoc dto :fields (:fields form))
    dto))

(defn update-post-dto
  [dto form]
  (if (= nil (:url-post dto))
    (assoc dto :url-post (:url_post form))
    dto))

(defn update-get-dto
  [dto form]
  (if (= nil (:url-get dto))
    (assoc dto :url-get (:url_get form))
    dto))


(defn update-form
  [ds form-id dto form]
  (let [update-name (update-name-dto dto form)
        update-fields (update-fields-dto update-name form)
        update-post (update-post-dto update-fields form)
        update-get (update-get-dto update-post form)]
    (database/execute-one!
      ds
      {:update [:form]
       :set {:name (:name update-get)
             :fields (:fields update-get)
             :url-post (:url-post update-get)
             :url-get (:url-get update-get)}
       :where [:= :id form-id]
       :returning [:id]})))


(defn get-all-data
  [ds id]
  (database/execute!
   ds
   {:select [:data]
    :from [:forms_data]
    :where [:= :form_id id]}))