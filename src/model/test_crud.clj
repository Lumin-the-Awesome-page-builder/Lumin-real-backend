(ns model.test-crud
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defrecord CreateTestCRUD [value])

(defn get-all [datasource]
  (jdbc/execute!
   (datasource)
   (-> {:select :*
        :from [:TestCRUD]}
       (sql/format))
   {:builder-fn rs/as-unqualified-kebab-maps}))

(defn get-one
  [datasource id]
  (jdbc/execute-one!
   (datasource)
   (-> {:select :*
        :from [:TestCRUD]
        :where [:= :id id]}
       (sql/format))
   {:builder-fn rs/as-unqualified-kebab-maps}))

(defn create
  [datasource ^CreateTestCRUD dto]
  (jdbc/execute-one!
   (datasource)
   (-> {:insert-into [:TestCRUD]
        :columns [:value]
        :values [[(:value dto)]]
        :returning :*}
       (sql/format))
   {:builder-fn rs/as-unqualified-kebab-maps}))


