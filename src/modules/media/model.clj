(ns modules.media.model
  (:require [utils.database :as database]))

(defn get-user-media
  [ds user-id]
  (database/execute!
   ds
   {:select [:id :name]
    :from [:media]
    :order-by [[:created_at :desc]]
    :where [:= :owner_id user-id]}))

(defn add-media
  [ds user-id name]
  (database/execute-one!
   ds
   {:insert-into [:media]
    :columns [:owner_id :name :created_at]
    :values [[user-id name (System/currentTimeMillis)]]
    :returning :*}))

(defn attach-to-project
  [ds media-id project-id]
  (database/execute-one!
   ds
   {:insert-into [:project_media]
    :columns [:project_id :media_id]
    :values [[project-id media-id]]
    :returning :*}))

(defn media-attached?
  [ds media-id project-id]
  (-> (database/execute-one!
       ds
       {:select [:project_media.media_id]
        :from [:project_media]
        :where [:and
                [:= :project_media.media_id media-id]
                [:= :project_media.project_id project-id]]})
      some?))

(defn get-attached-media
  [ds project-id]
  (database/execute!
   ds
   {:select [[:project_media.media_id] [:media.name]]
    :from [:project_media]
    :left-join [[:media] [:= :project_media.media_id :media.id]]
    :where [:= :project_media.project_id project-id]}))

(defn remove-from-project
  [ds media-id project-id]
  (database/execute-one!
   ds
   {:delete-from [:project_media]
    :where [:and
            [:= :project_media.media_id media-id]
            [:= :project_media.project_id project-id]]}))
