(ns modules.project.service
  (:refer-clojure :exclude [remove])
  (:require [clojure.data.json :as json]
            [modules.project.model :refer [get-project patch-project create-project set-tags get-tags remove-project]]
            [utils.validator :as validator]))

(defn- has-access? [user-id project]
  (when (not project)
    (throw (ex-info "Not found" {:errors "Project not found"})))
  (if (and project (= (:owner_id project) user-id))
    project
    (throw (ex-info "Not found" {}))))

(defn get-by-id
  [ds authorized-id project-id]
  (let [project (->> project-id
                     (get-project ds)
                     (has-access? authorized-id))]
    (json/write-str (assoc project :tags (map :tag (get-tags ds project-id))))))

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
               [])]
    (json/write-str (assoc created :tags tags))))

(defn remove
  [ds authorized-id project-id]
  (->> project-id
       (get-project ds)
       (has-access? authorized-id))
  (let [remove-result (-> (remove-project ds project-id)
                          (:next.jdbc/update-count)
                          (> 0))]
    (json/write-str (if remove-result
                      {:status 200}
                      {:status 400}))))
