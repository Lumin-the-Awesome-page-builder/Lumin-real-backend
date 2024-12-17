(ns modules.editor.redis-model
  (:require [clj-redis.client :as redis]
            [clojure.tools.logging :as log]))

(defn clients-key [project-id]
  (str project-id "_clients"))

(defn tree-key [project-id]
  (str project-id "_tree"))

(defn secret-key [project-id]
  (str project-id "_secret"))

(defn blocked-key [project-id]
  (str project-id "_blocked"))

(defn get-current-editors [rds project-id]
  (redis/hkeys rds (clients-key project-id)))

(defn project-in-edit? [rds project-id]
  (redis/hexists rds "active" (str project-id)))

(defn add-editor [rds project-id user-id]
  (redis/hset rds (clients-key project-id) (str user-id "_" project-id) "1"))

(defn remove-editor [rds project-id user-id]
  (redis/hdel rds (clients-key project-id) (str user-id "_" project-id)))

(defn any-client-active? [rds project-id]
  (-> (redis/hkeys rds (clients-key project-id)) not-empty some?))

(defn clear-cache [rds project-id]
  (log/info "Clearing cache of:" project-id)
  (log/info "Clear active"
            (redis/hdel rds "active" (str project-id)))
  (log/info "Clear blocked"
            (->> (redis/hkeys rds (blocked-key project-id))
                 (mapv #(redis/hdel rds (blocked-key project-id) %))))
  (log/info "Clear secret"
            (redis/hdel rds "secret" (secret-key project-id)))
  (log/info "Clear tree"
            (redis/hdel rds "tree" (tree-key project-id)))
  (log/info "Clear clients"
            (->> (redis/hkeys rds (clients-key project-id))
                 (mapv #(redis/hdel rds (clients-key project-id) %)))))

(defn activate [rds project-id tree-data user-id]
  (log/info "Tree data" tree-data)
  (add-editor rds project-id user-id)
  (redis/hset rds "active" (str project-id) "active")
  (redis/hset rds "tree" (tree-key project-id) tree-data)
  tree-data)

(defn get-active [rds]
  (redis/hkeys rds "active"))

(defn get-current-tree [rds project-id]
  (redis/hget rds "tree" (tree-key project-id)))

(defn patch-tree [rds project-id tree]
  (redis/hset rds "tree" (tree-key project-id) tree))

(defn start-edit [rds project-id project-data user-id]
  (log/info "project in edit" (project-in-edit? rds project-id) project-data)
  (if (project-in-edit? rds project-id)
    (do
      (add-editor rds project-id user-id)
      (get-current-tree rds project-id))
    (activate rds project-id project-data user-id)))

(defn char-range [lo hi]
  (range (int lo) (inc (int hi))))

(def alpha-numeric
  (map char (concat
             (char-range \a \z)
             (char-range \A \Z)
             (char-range \0 \9))))

(defn rand-alpha-numeric []
  (rand-nth alpha-numeric))

(defn generate-secret [length]
  (apply str
         (take length
               (repeatedly rand-alpha-numeric))))

(defn get-or-create-secret [rds project-id]
  (log/info project-id (project-in-edit? rds project-id))
  (if (project-in-edit? rds project-id)
    (redis/hget rds "secret" (secret-key project-id))
    (redis/hset rds "secret" (secret-key project-id) (generate-secret 30))))

(defn block-element [rds project-id key]
  (if (project-in-edit? rds project-id)
    (redis/hset rds (blocked-key project-id) key key)
    (throw (ex-info "Bad request" {:errors "project isn`t active"}))))

(defn release-element [rds project-id key]
  (if (and (project-in-edit? rds project-id)
           (redis/hexists rds (blocked-key project-id) key))
    (let [path (redis/hget rds (blocked-key project-id) key)]
      (redis/hdel rds (blocked-key project-id) key)
      path)
    (throw (ex-info "Bad request" {:errors "project isn`t active or element is free"}))))

(defn get-blocked [rds project-id]
  (if (redis/exists rds (blocked-key project-id))
    (redis/hvals rds (blocked-key project-id))
    []))