(ns modules.media.service
  (:require [clojure.data.json :as json]
            [modules.media.model :as model]
            [utils.validator :as validator]
            [utils.file :as file]))

(defn get-user-media
  [ds authorized-id]
  (json/write-str (model/get-user-media ds authorized-id)))

(def UploadSpec
  [:map
   [:name :string]
   [:base64 :string]])

(defn upload-media
  [ds authorized-id upload-data]
  (let [validated (validator/validate UploadSpec upload-data)
        file-name (str (System/currentTimeMillis) (:name validated))]
    (file/save-base64-file (:base64 validated) file-name)
    (json/write-str (model/add-media ds authorized-id file-name))))

(defn attach-to-project
  [ds project-id media-id]
  (when (not (model/media-attached? ds media-id project-id))
    (model/attach-to-project ds media-id project-id))
  (json/write-str {:success true}))

(defn remove-from-project
  [ds project-id media-id]
  (model/remove-from-project ds media-id project-id)
  (json/write-str {:success true}))

(defn get-attached
  [ds project-id]
  (json/write-str (model/get-attached-media ds project-id)))