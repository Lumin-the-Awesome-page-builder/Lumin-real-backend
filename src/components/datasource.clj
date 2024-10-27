(ns components.datasource
  (:require [clojure.tools.logging :as log]
            [next.jdbc.connection :as connection]
            [components.config :refer [fetch-config]])
  (:import (com.zaxxer.hikari HikariDataSource)
           (org.flywaydb.core Flyway)))

(defn datasource-component
  []
  (connection/component
   HikariDataSource
   (assoc (:database (fetch-config))
          :init-fn (fn [datasource]
                     (log/info "Database initializer run (migration)")
                     (.migrate
                      (.. (Flyway/configure)
                          (dataSource datasource)
                       ; https://www.red-gate.com/blog/database-devops/flyway-naming-patterns-matter
                          (locations (into-array String ["classpath:database/migrations"]))
                          (table "schema_version")
                          (load)))))))
