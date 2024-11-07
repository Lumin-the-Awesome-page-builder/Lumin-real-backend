(ns modules.library.model
  (:require [utils.database :as database]))

(defn get-all-user-projects
  [ds user-id]
  (database/execute!
   ds
   {:select [:id :public :stars :name :created_at]
    :from [:project]
    :order-by [[:created_at :desc]]
    :where [:= :owner_id user-id]}))

(defn get-all-user-widgets
  [ds user-id]
  (database/execute!
   ds
   {:select [:id :public :stars :name :created_at]
    :from [:widget]
    :where [:= :owner_id user-id]}))
