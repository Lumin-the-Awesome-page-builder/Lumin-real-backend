(ns service.test-crud
  (:require [clojure.tools.logging :as log]
            [model.test-crud :as model]
            [clojure.data.json :as json])
  (:import (model.test_crud CreateTestCRUD)))

(defn get-all [datasource]
  (log/info "Get all test crud")
  (-> (model/get-all datasource)
      (json/write-str)))

(defn get-one
  [datasource id]
  (log/info "Get test crud by id =" id)
  (-> (model/get-one datasource (parse-long id))
      (json/write-str)))

(defn create
  [datasource ^CreateTestCRUD dto]
  (log/info "Create test crud from: " dto)
  (-> (model/create datasource dto)
      (json/write-str)))
