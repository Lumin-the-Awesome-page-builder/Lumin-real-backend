(ns modules.editor.service
  (:require [clojure.data.json :as json]
            [ring.websocket :as ws]
            [utils.jwt :as jwt]
            [modules.project.model :as pm]
            [modules.project.service :refer [get-by-id CollaborationTokenSpec hide-shared-secret]]
            [utils.validator :as validator]
            [modules.editor.redis-model :as redis]
            [components.config :refer [fetch-config]]))

(defn- collaboration-key-valid? [ds collaboration-key]
  (if-let [data (->> (jwt/unsign collaboration-key [:owner_id :project_id :key])
                     (validator/validate CollaborationTokenSpec))]
    (get-by-id ds (:project_id data))
    false))

(defn- start-edit [rds ds authorized-id project-id]
  (let [project (-> (get-by-id ds project-id authorized-id) (hide-shared-secret))
        tree (:data project)
        start-res (redis/start-edit rds project-id tree authorized-id)
        secret (redis/get-or-create-secret rds project-id)
        jws-secret (-> (fetch-config) :jwt-secret-editor)]
    {:ok (= start-res "OK")
     :project (dissoc project :data)
     :tree tree
     :access (-> {:secret secret} json/write-str (jwt/encrypt jws-secret))}))

(defn- start-collaboration [rds ds authorized-id collaboration-key]
  (if-let [project-data (collaboration-key-valid? ds collaboration-key)]
    (let [tree (:data project-data)
          start-res (redis/start-edit rds (:id project-data) tree authorized-id)
          secret (redis/get-or-create-secret rds (:id project-data))
          jws-secret (-> (fetch-config) :jwt-secret-editor)]
      {:ok (= start-res "OK")
       :project (-> project-data
                    (hide-shared-secret)
                    (dissoc :data))
       :tree tree
       :access (-> {:secret secret :iat (System/currentTimeMillis)} json/write-str (jwt/encrypt jws-secret))})
    (throw (ex-info "Bad key" {:errors "bad key"}))))

(defn edit [rds ds authorized-id id-or-access]
  (if (-> id-or-access parse-long some?)
    (start-edit rds ds authorized-id (parse-long id-or-access))
    (start-collaboration rds ds authorized-id id-or-access)))

(defn- change-by-path
  "
  [tree path] -> will remove element by specified path
  [tree path on-replace] -> will replace element by specified item and path
  "
  ([tree path]
   (change-by-path tree path nil))

  ([tree path on-replace]
   (let [setup (:setup tree)
         {:keys [rootOrdering]} setup
         [new-tree new-ordering] (if (some? on-replace)
                                   (change-by-path tree rootOrdering path on-replace false)
                                   (change-by-path tree rootOrdering path nil true))]
     (->> new-ordering
          (assoc setup :rootOrdering)
          (assoc new-tree :setup))))

  ([tree ordering path on-replace remove?]
   (when (not tree)
     (throw (ex-info "Bad request" {:error "Bad path provided"})))
   (let [key (-> path first keyword)]
     (if (= 1 (count path))
       (if remove?
         [(dissoc tree key)
          (take (-> ordering count dec) (filter #(not= (first path) %) ordering))]
         [(assoc tree key on-replace)
          ordering])
       (let [child (get tree key)
             {:keys [children childrenOrdering]} child
             [new-children new-ordering] (change-by-path children childrenOrdering (drop 1 path) on-replace remove?)]
         [(assoc tree key (-> child
                              (assoc :children new-children)
                              (assoc :childrenOrdering new-ordering)))
          ordering])))))

(defn- find-element [tree path]
  (when (not tree)
    (throw (ex-info "Bad request" {:error "Bad path provided"})))
  (let [key (-> path first keyword)]
    (if (= 1 (count path))
      (get tree key)
      (recur (get tree key) (drop 1 path)))))

(defn- notice-editors [rds clients project-id message excluded]
  (let [project-clients (redis/get-current-editors rds project-id)]
    (doseq [client @clients]
      (when (and (not= (first client) excluded) (contains? project-clients (first client)))
        (ws/send (second client) (json/write-str message))))))

(defn- validate-access [rds access project-id]
  (-> access
      (jwt/validate)
      (not= (redis/get-or-create-secret rds project-id))
      (when (throw (ex-info "Bad request" {:errors "Bad access"})))))

(def PatchProjectTreeSpec
  [:map
   [:path [:sequential string?]]
   [:data :map]
   [:access string?]
   [:project_id int?]])

(defn patch-tree
  [rds patch-project-tree authorized-id clients]
  (let [validated (validator/validate PatchProjectTreeSpec patch-project-tree)
        _ (validate-access rds (:access validated) (:project_id validated))
        project (redis/get-current-tree rds (:project-id validated))
        root-children (-> (json/read-json project))
        replaced-tree (-> (change-by-path root-children (:path validated) (:data validated))
                          (json/write-str))]
    (redis/patch-tree rds (:project-id validated) replaced-tree)
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type "patch" :data patch-project-tree}
                    authorized-id)
    {:ok true}))

(def PatchTreeItemPropSpec
  [:map
   [:prop_name string?]
   [:prop_value {:optional true} string?]
   [:action [:enum "replace" "remove" "add"]]
   [:path [:sequential string?]]
   [:access string?]
   [:project_id int?]])

(defn update-prop
  [rds patch-tree-item-prop authorized-id clients]
  (let [validated (validator/validate PatchTreeItemPropSpec patch-tree-item-prop)
        _ (validate-access rds (:access validated) (:project_id validated))
        path (:path validated)
        tree (->> (:project_id validated)
                  (redis/get-current-tree rds)
                  (json/read-json))
        element (find-element tree path)
        props (:props element)
        prop-name (:prop_name validated)
        prop-value (get validated :prop_value nil)]
    (->> (case (:action validated)
           "replace"
           (map (fn [prop]
                  (if (= (:name prop) prop-name)
                    prop-value
                    (:value prop))) props)
           "remove"
           (filter #(not= (:name %) prop-name) props)

           "add"
           (conj props {:name prop-name :value prop-value}))
         (assoc element :props)
         (change-by-path tree path)
         (redis/patch-tree rds (:project_id validated)))
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type (str "prop-patch-" (:action validated))}
                    authorized-id)
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
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type "block" :data path}
                    authorized-id)
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
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type "release" :data path}
                    authorized-id)
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
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type "remove-element" :data (:path validated)}
                    authorized-id)
    {:ok true}))
