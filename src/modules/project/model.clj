(ns modules.project.model
  (:require [utils.database :as database]))

(defn get-project
  [ds id]
  (database/execute-one!
   ds
   {:select :*
    :from [:project]
    :where [:= :id id]}))

(defn create-project
  [ds dto]
  (database/execute-one!
   ds
   {:insert-into [:project]
    :columns [:name :data :owner_id :category_id :created_at]
    :values [[(:name dto) (:data dto) (:owner_id dto) (:category_id dto) (System/currentTimeMillis)]]
    :returning :*}))

(defn set-tags
  [ds id tags]
  (let [pairs (mapv (fn [tag] [id tag]) tags)]
    (database/execute-one!
     ds
     {:delete-from [:project_tags]
      :where [:= :project_id id]})
    (database/execute!
     ds
     {:insert-into [:project_tags]
      :columns [:project_id :tag]
      :values pairs
      :returning [:tag]})))

(defn get-tags
  [ds project-id]
  (database/execute!
   ds
   {:select [:tag]
    :from [:project_tags]
    :where [:= :project_id project-id]}))

(defn patch-project
  [ds id dto]
  (database/execute-one!
   ds
   {:update [:project]
    :set {:name (:name dto)
          :data (:data dto)
          :category_id (:category_id dto)
          :created_at (:created_at dto)}
    :where [:= :id id]}))

(defn remove-project
  [ds id]
  (database/execute-one!
   ds
   {:delete-from [:project]
    :where [:= :id id]}))
