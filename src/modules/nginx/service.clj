(ns modules.nginx.service
  (:refer-clojure :exclude [remove])
  (:require [clojure.data.json :as json]
            [me.raynes.fs :as fs]
            [modules.forms.service :refer [has-access-project?]]
            [modules.project.model :refer [get-project-by-domain update-domain-name]]
            [utils.file :refer [save-base64-file-custom-prefix]]
            [utils.validator :as validator]
            [components.config :refer [fetch-config]]))

(def UpdateIndexSpec [:map
                      [:data :string]])

(def DomainNameSpec [:map
                     [:name :string]])

(defn- get-domain-path [domain-name]
  (str (:deployment-base-path (fetch-config)) "/" domain-name))

(defn deploy
  [ds authorized-id project-id data]
  (let [validated (validator/validate DomainNameSpec data)
        project (has-access-project? ds authorized-id project-id)]
    (when (->> validated :name (get-project-by-domain ds) some?)
      (throw (ex-info "Bad request" {:errors "This Domain Name is already in use"})))
    (when (:domain_name project)
      (fs/delete-dir (:domain_name project-id)))
    (json/write-str (update-domain-name ds project-id (:name validated)))))

(defn update-index
  [ds authorized-id project-id data]
  (let [validated (validator/validate UpdateIndexSpec data)
        project (has-access-project? ds authorized-id project-id)
        domain-name (:domain_name project)]
    (when (nil? domain-name)
      (throw (ex-info "Bad request" {})))
    (fs/delete-dir (get-domain-path domain-name))
    (fs/mkdir (get-domain-path domain-name))
    (json/write-str
     (save-base64-file-custom-prefix (:data validated) (str (get-domain-path domain-name) "/index.html")))))