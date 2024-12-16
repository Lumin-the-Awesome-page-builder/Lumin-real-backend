(ns modules.editor.redis-model
  (:require [clj-redis.client :as redis]))

(defn clients-key [project-id]
  (str project-id "_clients"))

(defn project-active-key [project-id]
  (str project-id "_active"))

(defn tree-key [project-id]
  (str project-id "_tree"))

(defn secret-key [project-id]
  (str project-id "_secret"))

(defn blocked-key [project-id]
  (str project-id "_blocked"))

(defn get-current-editors [rds project-id]
  (redis/hvals rds (clients-key project-id)))

(defn project-in-edit? [rds project-id]
  (redis/exists rds (project-active-key project-id)))

(defn add-editor [rds project-id user-id]
  (redis/hset rds (clients-key project-id) (str user-id) "1"))

(defn remove-editor [rds project-id user-id]
  (redis/hdel rds (clients-key project-id) (str user-id)))

(defn any-client-active? [rds project-id]
  (-> (redis/hkeys rds (clients-key project-id)) not-empty some?))

(defn activate [rds project-id tree-data user-id]
  (add-editor rds project-id user-id)
  (redis/set rds (project-active-key project-id) "active")
  (redis/set rds (tree-key project-id) tree-data))

(defn get-current-tree [rds project-id]
  (redis/get rds (tree-key project-id)))

(defn patch-tree [rds project-id tree]
  (redis/set rds (tree-key project-id) tree))

(defn start-edit [rds project-id project-data user-id]
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
  (if (project-in-edit? rds project-id)
    (redis/get rds (secret-key project-id))
    (redis/set rds (secret-key project-id) (generate-secret 30))))

(defn block-element [rds project-id path]
  (if (project-in-edit? rds project-id)
    (let [key (str (System/currentTimeMillis))]
      (redis/hset rds (blocked-key project-id) key path)
      key)
    (throw (ex-info "Bad request" {:errors "project isn`t active"}))))

(defn release-element [rds project-id key]
  (if (and (project-in-edit? rds project-id)
           (redis/hexists rds (blocked-key project-id) key))
    (let [path (redis/hget rds (blocked-key project-id) key)]
      (redis/hdel rds (blocked-key project-id) key)
      path)
    (throw (ex-info "Bad request" {:errors "project isn`t active or element is free"}))))
