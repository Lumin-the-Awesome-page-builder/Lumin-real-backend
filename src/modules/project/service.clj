(ns modules.project.service
  (:refer-clojure :exclude [remove])
  (:require [clojure.data.json :as json]
            [modules.project.model :refer [get-project patch-project patch-project-preview create-project set-tags get-tags remove-project patch-share]]
            [modules.docker.service :refer [generate-docker-directory]]
            [utils.validator :as validator]
            [utils.file :as f]
            [utils.jwt :as jwt]
            [components.config :refer [fetch-config]]))

(defn- has-access? [user-id project]
  (when (not project)
    (throw (ex-info "Not found" {:errors "Project not found"})))
  (if (and project (= (:owner_id project) user-id))
    project
    (throw (ex-info "Not found" {}))))

(defn get-by-id
  ([ds project-id]
   (let [project (get-project ds project-id)]
     (assoc project :tags (map :tag (get-tags ds project-id)))))
  ([ds project-id authorized-id]
   (let [project (->> project-id
                      (get-project ds)
                      (has-access? authorized-id))]
     (assoc project :tags (map :tag (get-tags ds project-id))))))

(defn hide-shared-secret
  [project-data]
  (assoc project-data :shared (some? (:shared project-data))))

(def ProjectPatchSpec
  [:map
   [:name {:optional true} :string]
   [:data {:optional true} :string]
   [:category_id {:optional true} int?]
   [:tags {:optional true} [:sequential string?]]])

(defn patch
  [ds authorized-id project-id patch-data]
  (let [validated (validator/validate ProjectPatchSpec patch-data)
        project (->> project-id
                     (get-project ds)
                     (has-access? authorized-id))
        patch-result (-> (patch-project ds project-id (into project validated))
                         (:next.jdbc/update-count)
                         (> 0))
        tags (map :tag (if (:tags validated)
                         (set-tags ds project-id (:tags validated))
                         (get-tags ds project-id)))]
    (json/write-str (if patch-result
                      (-> (get-project ds project-id)
                          (assoc :tags tags))
                      {:status 400}))))

(def ProjectCreateSpec
  [:map
   [:name :string]
   [:data :string]
   [:category_id {:optional true} int?]
   [:tags {:optional true} [:sequential string?]]])

(defn create
  [ds authorized-id project-data]
  (let [validated (validator/validate ProjectCreateSpec project-data)
        created (create-project ds (assoc validated :owner_id authorized-id))
        tags (if (:tags validated)
               (map :tag (set-tags ds (:id created) (:tags validated)))
               [])
        project-id (:id created)]
    (generate-docker-directory project-id)
    (json/write-str (assoc created :tags tags))))

(defn remove
  [ds authorized-id project-id]
  (let [project (->> project-id
                     (get-project ds)
                     (has-access? authorized-id))
        remove-result (-> (remove-project ds project-id)
                          (:next.jdbc/update-count)
                          (> 0))]
    (when (:preview project)
      (f/drop-file (:preview project)))

    (json/write-str (if remove-result
                      {:status 200}
                      {:status 400}))))

(defn patch-preview
  [ds authorized-id project-id preview]
  (let [project (->> project-id
                     (get-project ds)
                     (has-access? authorized-id))
        preview-file-name (str "Project" (System/currentTimeMillis) (:id project) ".png")]
    (when (:preview project)
      (f/drop-file (:preview project)))
    (patch-project-preview ds project-id preview-file-name)
    (f/save-base64-file preview preview-file-name)
    (json/write-str {:status 200})))

(def CollaborationTokenSpec
  [:map
   [:owner_id int?]
   [:project_id int?]
   [:key string?]])

(defn create-collaboration-link
  [ds authorized-id project-id]
  (let [project (->> project-id
                     (get-project ds)
                     (has-access? authorized-id))
        shared (:shared project)]
    (if (some? shared)
      (if-let [token (jwt/sign {:owner_id authorized-id :project_id project-id :key shared} (:collaboration-token-expiring-time (fetch-config)))]
        (json/write-str {:access_token token})
        (throw (ex-info "Internal server error" {:errors "link generation failed"})))
      (throw (ex-info "Bad request" {:errors "share project before link creation"})))))

(def ShareProjectSpec
  [:map
   [:marketplace boolean?]
   [:collaboration boolean?]])

(defn share [ds authorized-id project-id share-dto]
  (->> project-id
       (get-project ds)
       (has-access? authorized-id))
  (let [{:keys [marketplace collaboration]} (validator/validate ShareProjectSpec share-dto)
        collaboration (if collaboration (str (System/currentTimeMillis)) nil)]
    (patch-share ds project-id {:shared collaboration :shared_marketplace marketplace}))
  (json/write-str {:status 200}))
