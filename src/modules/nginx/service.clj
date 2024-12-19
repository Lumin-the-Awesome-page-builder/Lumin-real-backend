(ns modules.nginx.service
  (:refer-clojure :exclude [remove])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [modules.forms.service :refer [has-access-project?]]
            [modules.project.model :refer [insert-nginx-path-and-domain-name get-nginx-path-and-domain-name get-project-by-domain]]
            [modules.docker.service :refer [execute-host-docker-command]]
            [components.config :refer [fetch-config]]
            [clojure.java.shell :refer [sh]]
            [utils.file :refer [save-base64-file-custom-prefix]]
            [utils.validator :as validator]))

(def UpdateIndexSpec [:map
                      [:data :string]])

(def DomainNameSpec [:map
                     [:name :string]])

(defn has-access-domain-name?
  [ds domain-name]
  (let [project (get-project-by-domain ds domain-name)]
    (println project)
    (when project
      (throw (ex-info "Bad request" {:errors "This Domain Name is already used"})))
    project))

(defn copy-dir
  [source-path target-path dir]
  (log/info "Copy" (str source-path "/") target-path dir)
  (sh "cp" "-r" (str source-path "/") target-path :dir dir))

(defn create-nginx-directory
  [ds authorised-id project-id domain-name]
  (let [docker-path (-> (fetch-config) :docker-path)
        docker-host-path (-> (fetch-config) :docker-host-path)
        validated (validator/validate DomainNameSpec domain-name)
        project (has-access-project? ds authorised-id project-id)
        path (str authorised-id "/" project-id)
        nginx-base-dir (io/file docker-path "nginx-base")
        nginx-dir (io/file docker-host-path path)
        user-nginx-path (str (.getAbsolutePath nginx-dir) "/nginx-base")
        replacements {:path_to_user_dir user-nginx-path
                      :domain_name (:name validated)}]
    (has-access-domain-name? ds (:name validated))
    (if (.exists nginx-dir)
      (throw (ex-info "Bad request" {}))
      (do
        (.mkdirs nginx-dir)
        (str user-nginx-path "/nginx.conf")
        (copy-dir (.getAbsolutePath nginx-base-dir) (.getAbsolutePath nginx-dir) (.getAbsolutePath nginx-base-dir))
        (let [template (slurp (str user-nginx-path "/nginx.conf"))
              updated-content
              (string/replace template #"\{\{(.+?)\}\}"
                              (fn [[_ key]]
                                (get replacements (keyword key) "")))]
          (spit (str user-nginx-path "/nginx.conf") updated-content))
        (insert-nginx-path-and-domain-name ds (:id project) user-nginx-path (:name validated))
        (json/write-str {:success "true"})))))

(defn update-index
  [ds authorise-id project-id data]
  (let [validated (validator/validate UpdateIndexSpec data)
        project (has-access-project? ds authorise-id project-id)
        path (:nginx_path project)]
    (if (= nil path)
      (throw (ex-info "Bad request" {}))
      (save-base64-file-custom-prefix (:data validated) (str path "/index.html")))))

(defn reload-nginx
  [ds authorised-id project-id]
  (let [project (has-access-project? ds authorised-id project-id)
        path-and-domain (get-nginx-path-and-domain-name ds (:id project))
        file-path (str (:nginx_path path-and-domain) "/nginx.conf")
        link-to-domain (str "/etc/nginx/sites-enabled/" (:domain_name path-and-domain) ".dudosyka.ru")]
    (do
      (execute-host-docker-command (:nginx_path path-and-domain) "ln" "-s" file-path link-to-domain)
      (execute-host-docker-command (:nginx_path path-and-domain) "certbot" "--nginx" "-d" (str (:domain_name path-and-domain) ".dudosyka.ru"))
      (execute-host-docker-command (:nginx_path path-and-domain) "systemctl" "restart" "nginx")
      (json/write-str {:success "true"}))))