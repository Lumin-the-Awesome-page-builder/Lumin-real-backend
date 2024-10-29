(ns modules.widget.model
  (:require [utils.database :as database]))

(defn get-widget
  [ds id]
  (database/execute-one!
   ds
   {:select :*
    :from [:widget]
    :where [:= :id id]}))

(defn create-widget
  [ds dto]
  (database/execute-one!
   ds
   {:insert-into [:widget]
    :columns [:name :data :owner_id :category_id :created_at]
    :values [[(:name dto) (:data dto) (:owner_id dto) (:category_id dto) (System/currentTimeMillis)]]
    :returning :*}))

(defn set-tags
  [ds widget-id tags]
  (let [pairs (mapv (fn [tag] [widget-id tag]) tags)]
    (database/execute-one!
     ds
     {:delete-from [:widget_tags]
      :where [:= :widget_id widget-id]})
    (database/execute!
     ds
     {:insert-into [:widget_tags]
      :columns [:widget_id :tag]
      :values pairs
      :returning [:tag]})))

(defn get-tags
  [ds widget-id]
  (database/execute!
   ds
   {:select [:tag]
    :from [:widget_tags]
    :where [:= :widget_id widget-id]}))

(defn patch-widget
  [ds widget-id dto]
  (database/execute-one!
   ds
   {:update [:widget]
    :set {:name (:name dto)
          :data (:data dto)
          :category_id (:category_id dto)
          :created_at (:created_at dto)}
    :where [:= :id widget-id]}))

(defn remove-widget
  [ds widget-id]
  (database/execute-one!
   ds
   {:delete-from [:widget]
    :where [:= :id widget-id]}))