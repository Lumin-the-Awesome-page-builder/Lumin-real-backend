(ns modules.editor.service
  (:require [clojure.data.json :as json]
            [ring.websocket :as ws]
            [utils.jwt :as jwt]
            [modules.project.model :as pm]
            [modules.project.service :refer [get-by-id CollaborationTokenSpec]]
            [utils.validator :as validator]
            [modules.editor.redis-model :as redis]))

(defn- collaboration-key-valid? [ds collaboration-key]
  (if-let [data (->> collaboration-key
                     (jwt/unsign)
                     (validator/validate CollaborationTokenSpec))]
    (pm/get-project ds (:project_id data))
    false))

(defn- start-edit [rds ds authorized-id project-id]
  (let [project (get-by-id ds authorized-id project-id)
        tree (json/write-str (:data project))
        start-res (redis/start-edit rds project-id tree authorized-id)
        secret (redis/get-or-create-secret rds project-id)]
    {:ok (= start-res "OK")
     :tree tree
     :access (jwt/encrypt (json/write-str {:secret secret}))}))

(defn- start-collaboration [rds ds authorized-id collaboration-key]
  (if-let [project-data (collaboration-key-valid? ds collaboration-key)]
    (let [tree (redis/start-edit rds (:id project-data) (:data project-data) authorized-id)
          secret (redis/get-or-create-secret rds (:id project-data))]
      {:ok true
       :tree tree
       :access (jwt/encrypt {:secret secret})})
    (throw (ex-info "Bad key" {:errors "bad key"}))))

(defn edit [rds ds authorized-id project-id data]
  (if (some? (:access data))
    (start-collaboration rds ds authorized-id (:access data))
    (start-edit rds ds authorized-id project-id)))

(defn- change-by-path
  "
  [obj path] -> will remove element by specified path
  [obj path on-replace] -> will replace element by specified item and path
  "
  ([obj path]
   (change-by-path obj path (last path) true))

  ([obj path on-replace]
   (change-by-path obj path on-replace false))

  ([obj path on-replace remove?]
   (when (not obj)
     (throw (ex-info "Bad request" {:error "Bad path provided"})))
   (if (zero? (count path))
     (let [key-on-replace (keyword (:key on-replace))]
       (if remove?
         (dissoc obj key-on-replace)
         (assoc obj key-on-replace on-replace)))
     (let [on-search (keyword (first path))
           replaced (change-by-path (->> on-search
                                         (get obj)
                                         (:children)) (drop 1 path) on-replace)
           replaced (assoc (get obj on-search) :children replaced)]
       (assoc obj on-search replaced)))))

(def PatchProjectTreeSpec
  [:map
   [:path [:sequential string?]]
   [:data :map]
   [:access string?]
   [:project_id int?]])

(defn- notice-editors [clients message excluded]
  (doseq [client @clients]
    (when (not= (first client) excluded)
      (ws/send (second client) (json/write-str message)))))

(defn- validate-access [rds access project-id]
  (-> access
      (jwt/validate)
      (not= (redis/get-or-create-secret rds project-id))
      (when (throw (ex-info "Bad request" {:errors "Bad access"})))))

(defn patch-tree
  [rds patch-project-tree authorized-id clients]
  (let [validated (validator/validate PatchProjectTreeSpec patch-project-tree)
        _ (validate-access rds (:access validated) (:project_id validated))
        project (redis/get-current-tree rds (:project-id validated))
        root-children (-> (json/read-json project))
        replaced-tree (-> (change-by-path root-children (:path validated) (:data validated))
                          (json/write-str))]
    (redis/patch-tree rds (:project-id validated) replaced-tree)
    (notice-editors clients {:type "patch" :data patch-project-tree} authorized-id)
    {:ok true}))

(def BlockPathSpec
  [:map
   [:project_id int?
    :path [:sequential string?]
    :access string?]])

(defn block-element [rds block-element-data authorized-id clients]
  (let [validated (validator/validate BlockPathSpec block-element-data)
        _ (validate-access rds (:access validated) (:project_id validated))
        path (:path validated)]
    (redis/block-element rds (:project_id validated) path)
    (notice-editors clients {:type "block" :data path} authorized-id)
    {:ok true}))

(def ReleasePathSpec
  [:map
   [:project_id int?
    :index int?
    :access string?]])

(defn release-element [rds release-element-data authorized-id clients]
  (let [validated (validator/validate ReleasePathSpec release-element-data)
        _ (validate-access rds (:access validated) (:project_id validated))
        path (redis/release-element rds (:project_id validated) (:index validated))]
    (notice-editors clients {:type "release" :data path} authorized-id)
    {:ok true}))

(def ProjectIdSpec
  [:map
   :project_id int?
   :access string?])

(defn save-project [rds ds save-project-data]
  (let [validated (validator/validate ProjectIdSpec save-project-data)
        _ (validate-access rds (:access validated) (:project_id validated))]
    (if (redis/project-in-edit? rds (:project_id validated))
      (let [tree (redis/get-current-tree rds (:project-id validated))]
        (pm/patch-project ds (:project_id validated) {:data tree})
        {:ok true})
      (throw (ex-info "bad project" {:errors "project isn`t active"})))))

(defn close-edit [rds ds close-edit-data authorized-id]
  (let [validated (validator/validate ProjectIdSpec close-edit-data)
        _ (validate-access rds (:access validated) (:project_id validated))]
    (redis/remove-editor rds (:project_id validated) authorized-id)
    (when (not (redis/any-client-active? rds (:project_id validated)))
      (save-project rds ds close-edit-data))
    {:ok true}))

(def RemoveElementSpec
  [:map
   [:project_id int?
    :path [:sequential string?]
    :access string?]])
(defn remove-element [rds remove-element-data authorized-id clients]
  (let [validated (validator/validate RemoveElementSpec remove-element-data)
        _ (validate-access rds (:access validated) (:project_id validated))
        tree-on-update (-> (redis/get-current-tree rds (:project_id validated))
                           (change-by-path (:path validated)))]
    (redis/patch-tree rds (:project-id validated) tree-on-update)
    (notice-editors clients {:type "remove-element" :data (:path validated)} authorized-id)
    {:ok true}))
