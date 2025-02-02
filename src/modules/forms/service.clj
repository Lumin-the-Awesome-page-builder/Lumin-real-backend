(ns modules.forms.service
  (:refer-clojure :exclude [remove])
  (:require [clojure.data.json :as json]
            [modules.project.model :refer [get-project]]
            [modules.forms.model :refer [insert-form get-form-by-id insert-data-by-form get-all-data update-form]]
            [utils.validator :as validator]))

(def CreateFormSpec [:map
                     [:name :string]
                     [:fields :string]
                     [:url-post {:optional true} string?]
                     [:url-get {:optional true} string?]])

(defn has-access-project?
  [ds user-id project-id]
  (let [project (get-project ds project-id)]
    (when (not project)
      (throw (ex-info "Not found" {:errors "Project not found"})))
    (if (and project (= (:owner_id project) user-id))
      project
      (throw (ex-info "Not found" {})))))

(defn has-access-form?
  [ds user-id form-id]
  (let [form (get-form-by-id ds form-id)]
    (when (not form)
      (throw (ex-info "Not found" {:errors "Form not found"})))
    (if (and form (= (:owner_id form) user-id))
      form
      (throw (ex-info "Not found" {})))))

(defn create-form
  [ds authorised-id project-id form-data]
  (has-access-project? ds authorised-id project-id)
  (let [validated (validator/validate CreateFormSpec form-data)]
    (if validated
      (json/write-str (insert-form ds authorised-id validated))
      (throw (ex-info "Bed request" {:message "Bed data provided"})))))

(defn get-form-fields
  [ds authorised-id form-id]
  (let [form (has-access-form? ds authorised-id form-id)]
    (json/write-str {:success "true" :data (:fields form)})))

(def InsertFormSpec [:map
                     [:data :string]])

(defn post-data
  [ds _ form-id data]
  (let [form (get-form-by-id ds form-id)
        validated (validator/validate InsertFormSpec data)]
    (if validated
      (json/write-str (insert-data-by-form ds (:id form) (:data validated)))
      (throw (ex-info "Bed request" {:message "Bed data provided"})))))

(defn get-data
  [ds _ form-id]
  (let [form (get-form-by-id ds form-id)]
    (json/write-str (mapv (fn [row]
                            (json/read-json (:data row))) (get-all-data ds (:id form))))))

(def UpdateFormSpec [:map
                     [:name {:optional true} string?]
                     [:fields {:optional true} string?]
                     [:url-post {:optional true} string?]
                     [:url-get {:optional true} string?]])

(defn patch-form
  [ds authorised-id form-id patch-data]
  (let [form (has-access-form? ds authorised-id form-id)
        validated (validator/validate UpdateFormSpec patch-data)]
    (if (or (nil? (:url-post validated)) (= (:url-post validated) "https://example.com/save"))
      (json/write-str (update-form ds form-id (-> validated
                                                  (assoc :url-post (str "https://api.lumin.dudosyka.ru/lumin/form-handler/" form-id "/data"))
                                                  (assoc :url-get (str "https://api.lumin.dudosyka.ru/lumin/form/" form-id "/data"))) form))
      (json/write-str (update-form ds form-id validated form)))))