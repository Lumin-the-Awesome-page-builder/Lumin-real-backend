(ns utils.database
  (:require [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn execute! [ds query]
  (let [query (-> query
                  (sql/format))]
    (log/info "Execute:" (first query) (drop 1 query))
    (try
      (jdbc/execute!
       (ds)
       query
       {:builder-fn rs/as-unqualified-lower-maps})
      (catch Exception e
        (log/error "Error while executing request: " (.getMessage e))
        (throw e)))))

(defn execute-one! [ds query]
  (let [query (-> query
                  (sql/format))]
    (log/info "Execute:" (first query) (drop 1 query))
    (try
      (jdbc/execute-one!
       (ds)
       query
       {:builder-fn rs/as-unqualified-lower-maps})
      (catch Exception e
        (log/error "Error while executing request " (.getMessage e))
        (throw e)))))
