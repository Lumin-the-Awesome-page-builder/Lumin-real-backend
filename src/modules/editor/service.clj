(ns modules.editor.service
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
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
        tree (redis/start-edit rds project-id (:data project) authorized-id)
        secret (redis/get-or-create-secret rds project-id)
        jws-secret (-> (fetch-config) :jwt-secret-editor)]
    {:project (dissoc project :data)
     :tree tree
     :access (-> {:secret secret} json/write-str (jwt/encrypt jws-secret))}))

(defn- start-collaboration [rds ds authorized-id collaboration-key]
  (if-let [project-data (collaboration-key-valid? ds collaboration-key)]
    (let [tree (redis/start-edit rds (:id project-data) (:data project-data) authorized-id)
          secret (redis/get-or-create-secret rds (:id project-data))
          jws-secret (-> (fetch-config) :jwt-secret-editor)]
      {:project (-> project-data
                    (hide-shared-secret)
                    (dissoc :data))
       :tree tree
       :access (-> {:secret secret :time (System/currentTimeMillis)} json/write-str (jwt/encrypt jws-secret))})
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
   (log/info tree ordering path remove?)
   (let [key (-> path first keyword)]
     (if (= 1 (count path))
       (if remove?
         [(dissoc tree key)
          (take (-> ordering count dec) (filter #(not= (first path) %) ordering))]
         (if (nil? (get tree key))
           [(assoc tree key on-replace)
            (conj ordering (first path))]
           [(assoc tree key on-replace)
            ordering]))

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
      (recur (-> tree (get key) :children)  (drop 1 path)))))

(defn- notice-editors [rds clients project-id message excluded]
  (log/info "Start noticing...")
  (let [project-clients (mapv identity (redis/get-current-editors rds project-id))]
    (log/info "Current editors:" project-clients)
    (doseq [[client socket] @clients]
      (log/info "Client:" client (str excluded "_" project-id) socket)
      (when (and (not= client (str excluded "_" project-id)) (some #(= client %) project-clients))
        (log/info "Send to client:" client (json/write-str message))
        (ws/send socket (json/write-str message))))))

(defn- validate-access [rds access project-id]
  (log/info access)
  (-> access
      (jwt/validate (-> (fetch-config) :jwt-secret-editor))
      :secret
      (not= (redis/get-or-create-secret rds project-id))
      (when (throw (ex-info "Bad access" {:errors "Bad access"})))))

(def AuthSpec
  [:map
   [:project_id int?]
   [:access string?]])

(defn auth-client [rds clients auth-data authorized-id socket]
  (let [validated (validator/validate AuthSpec auth-data)
        project-id (:project_id validated)
        _ (validate-access rds (:access validated) project-id)
        already-active (get @clients (str authorized-id "_" project-id) nil)]
    (when (some? already-active)
      (log/info "Already active: " already-active)
      (ws/close already-active))
    (swap! clients assoc (str authorized-id "_" project-id) socket)
    {:ok true
     :type "auth-client"
     :blocked (redis/get-blocked rds project-id)}))

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
        project (redis/get-current-tree rds (:project_id validated))
        path (:path validated)
        root-children (json/read-json project)
        replaced-tree (-> (change-by-path root-children path (:data validated))
                          (json/write-str))]
    (redis/patch-tree rds (:project_id validated) replaced-tree)
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type "patch" :data {:json (-> validated :data json/write-str)
                                          :key (-> validated :data :key)
                                          :parent_key (if (> (count path) 1)
                                                        (nth path (- (count path) 2))
                                                        "root")}}
                    authorized-id)
    {:ok true}))

(def PatchTreeItemPropSpec
  [:map
   [:prop_name string?]
   [:prop_value {:optional true} any?]
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
                    (into prop {:value prop-value})
                    prop)) props)
           "remove"
           (filter #(not= (:name %) prop-name) props)

           "add"
           (conj props {:name prop-name :value prop-value}))
         (assoc element :props)
         (change-by-path tree path)
         (json/write-str)
         (redis/patch-tree rds (:project_id validated)))
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type (str "patch-prop-" (:action validated))
                     :prop_name (:prop_name validated)
                     :prop_value (:prop_value validated)
                     :key (-> validated :path last)}
                    authorized-id)
    {:ok true}))

(def PatchTreeItemOrderingSpec
  [:map
   [:ordering [:sequential string?]]
   [:path [:sequential string?]]
   [:access string?]
   [:project_id int?]])

(defn patch-item-ordering
  [rds patch-tree-item-ordering authorized-id clients]
  (let [validated (validator/validate PatchTreeItemOrderingSpec patch-tree-item-ordering)
        _ (validate-access rds (:access validated) (:project_id validated))
        project (redis/get-current-tree rds (:project_id validated))
        root-children (-> (json/read-json project))
        item (find-element root-children (:path validated))
        new-item (assoc item :childrenOrdering (:ordering validated))
        replaced-tree (-> (change-by-path root-children (:path validated) new-item)
                          (json/write-str))]
    (redis/patch-tree rds (:project_id validated) replaced-tree)
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type "patch-item-ordering"
                     :path (-> validated :path last)
                     :ordering (:ordering validated)}
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
        key (-> validated :path last)]
    (redis/block-element rds (:project_id validated) key)
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type "block" :data key}
                    authorized-id)
    {:ok true}))

(def ReleasePathSpec
  [:map
   [:project_id int?
    :path [:sequential string?]
    :access string?]])

(defn release-element [rds release-element-data authorized-id clients]
  (let [validated (validator/validate ReleasePathSpec release-element-data)
        _ (validate-access rds (:access validated) (:project_id validated))
        key (-> validated :path last)]
    (redis/release-element rds (:project_id validated) key)
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type "release" :data key}
                    authorized-id)
    {:ok true}))

(def ProjectIdSpec
  [:map
   [:project_id int?]
   [:access string?]])

(defn save-project [rds ds save-project-data]
  (let [validated (validator/validate ProjectIdSpec save-project-data)
        _ (validate-access rds (:access validated) (:project_id validated))]
    (if (redis/project-in-edit? rds (:project_id validated))
      (let [tree (redis/get-current-tree rds (:project_id validated))]
        (pm/patch-tree ds (:project_id validated) tree)
        {:ok true})
      (throw (ex-info "bad project" {:errors "project isn`t active"})))))

(defn close-edit [rds close-edit-data authorized-id]
  (let [validated (validator/validate ProjectIdSpec close-edit-data)
        _ (validate-access rds (:access validated) (:project_id validated))]
    (redis/remove-editor rds (:project_id validated) authorized-id)
    (when (not (redis/any-client-active? rds (:project_id validated)))
      (log/info "NO ACTIVE CLIENTS")
      (redis/clear-cache rds (:project_id validated)))
    {:ok true}))

(defn on-close [clients rds ds close-args]
  (log/info "Socket closed")
  (log/info (count @clients))
  (log/info close-args)
  (let [closed (first close-args)
        active-clients (->> @clients
                            (filter (fn [[client socket]] (let [open? (ws/open? socket)]
                                                            (log/info client open?)
                                                            (when (or (not open?) (= closed socket))
                                                              (swap! clients dissoc client))
                                                            (and open? (not= closed socket)))))
                            (mapv #(-> % first (string/split #"_") second))
                            set)]
    (log/info "ws clients" @clients)
    (log/info "active clients" active-clients)
    (doseq [project (redis/get-active rds)]
      (when (not (contains? active-clients project))
        (->> project
             (redis/get-current-tree rds)
             (pm/patch-tree ds (parse-long project)))
        (redis/clear-cache rds (parse-long project))))))

(def RemoveElementSpec
  [:map
   [:project_id int?]
   [:path [:sequential string?]]
   [:access string?]])
(defn remove-element [rds remove-element-data authorized-id clients]
  (let [validated (validator/validate RemoveElementSpec remove-element-data)
        _ (validate-access rds (:access validated) (:project_id validated))
        tree-on-update (-> (redis/get-current-tree rds (:project_id validated))
                           (json/read-json)
                           (change-by-path (:path validated)))]
    (redis/patch-tree rds (:project_id validated) (json/write-str tree-on-update))
    (notice-editors rds
                    clients
                    (:project_id validated)
                    {:type "remove-element" :data (last (:path validated))}
                    authorized-id)
    {:ok true}))
