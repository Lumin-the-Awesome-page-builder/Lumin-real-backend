(ns modules.library.model
  (:require [utils.database :as database]))

(defn get-all-user-projects
  [ds user-id]
  (database/execute!
   ds
   {:select [:id :public :stars :name]
    :from [:project]
    :where [:= :owner_id user-id]}))

(defn get-all-user-widgets
  [ds user-id]
  (database/execute!
   ds
   {:select [:id :public :stars :name]
    :from [:widget]
    :where [:= :owner_id user-id]}))
