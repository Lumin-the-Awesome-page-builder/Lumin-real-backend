(ns modules.docker.model
  (:require [utils.database :as database]))

(defn get-all-user-environment
  [ds user-id]
  (database/execute!
   ds
   {:select [:id :name :created_at]
    :from [:environment]
    :order-by [[:created_at :desc]]
    :where [:and [:= :owner_id user-id] [:= :hidden false]]}))

(defn get-environment-by-id
  [ds environment-id]
  (database/execute!
   ds
   {:select [:environment.*]
    :from [:environment]
    :where [:and [:= :id environment-id] [:= :hidden false]]}))

(defn get-environments-by-user
  [ds user-id]
  (database/execute!
   ds
   {:select [:environment.name]
    :from [:environment]
    :where [:= :owner_id user-id]}))

(defn get-container-by-id
  [ds container-id]
  (database/execute-one!
   ds
   {:select [:container.*]
    :from [:container]
    :where [:= :id container-id]}))

(defn get-container-by-name
  [ds name]
  (database/execute!
   ds
   {:select [:container.*]
    :from [:container]
    :where [:= :name name]}))

(defn create-environment
  [ds user-id name path hidden]
  (let [environments (get-environments-by-user ds user-id)]
    (if (some #(= (:name %) name) environments)
      (throw (ex-info "Bad request"
                      {:environment-name name :message "This name already is used"}))
      (if hidden
        (database/execute-one!
         ds
         {:insert-into [:environment]
          :columns [:name :owner_id :path :created_at :hidden]
          :values [[name user-id path (System/currentTimeMillis) hidden]]
          :returning [:id :name]})
        (database/execute-one!
         ds
         {:insert-into [:environment]
          :columns [:name :owner_id :path :created_at :hidden]
          :values [[name user-id path (System/currentTimeMillis) false]]
          :returning [:id :name]})))))

(defn insert-or-update-container
  [ds name status environment-id]
  (let [container (get-container-by-name ds name)]
    (if (empty? container)
      (database/execute!
       ds
       {:insert-into [:container]
        :columns [:name :status :environment_id :created_at :hidden]
        :values [[name status environment-id (System/currentTimeMillis) false]]
        :returning :*})
      (database/execute!
       ds
       {:update [:container]
        :set {:status status
              :environment_id environment-id}
        :where [:= :name name]
        :returning :*}))))

(defn get-all-configurations
  [ds]
  (database/execute!
   ds
   {:select [:id, :name, :mapping]
    :from [:configuration]}))

(defn get-configuration
  [ds configuration-id]
  (database/execute-one!
   ds
   {:select [:id, :name, :mapping]
    :from [:configuration]
    :where [:= :id configuration-id]}))

(defn get-configuration-full
  [ds configuration-id]
  (database/execute-one!
   ds
   {:select [:id, :name, :mapping, :path]
    :from [:configuration]
    :where [:= :id configuration-id]}))