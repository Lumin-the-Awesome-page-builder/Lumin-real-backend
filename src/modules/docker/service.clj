(ns modules.docker.service
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [components.config :refer [fetch-config]]
            [clojure.java.shell :refer [sh]]
            [clojure.data.json :as json]
            [modules.docker.model :refer [get-environment-by-id create-environment get-container-by-id
                                          insert-or-update-container get-all-user-environment get-all-configurations
                                          get-configuration get-configuration-full]]
            [utils.file :as f]
            [utils.validator :as validator]
            [babashka.http-client :as http])
  (:import (java.io BufferedReader)))

(defn check-directory-existence [path]
  (log/info "Check dir exists: " path)
  (-> path io/file .exists))

(def ContainerCreateSpec [:map
                          [:name :string]])

(defn has-access-env?
  [ds authorised-id environment-id]
  (let [env (first (get-environment-by-id ds environment-id))]
    (when (not env)
      (throw (ex-info "Not found" {:errors "Environment not found"})))
    (if (= (str (:owner_id env)) (str authorised-id))
      env
      (do
        (log/info "bad access to environment, user=" authorised-id, "env=", env)
        (throw (ex-info "Not found" {}))))))

(defn execute-host-docker-command [dir & command]
  (-> (http/post "http://host.docker.internal:9090" {:headers {:content-type "application/json"}
                                                     :body (json/write-str {:command command :dir dir})})
      :body
      json/read-json))

(defn list-containers [dir]
  (let [{:keys [out exit]} (execute-host-docker-command dir "docker" "compose" "ps" "--format" "{{.Names}} {{.Status}}")]
    (if (= exit 0)
      (json/write-str
       (map #(let [[name & status] (str/split % #" " 2)]
               {:name name, :status (str/join " " status)})
            (str/split-lines out)))
      (throw (ex-info "Error executing docker command" {:error "Not found"})))))

(defn generate-docker-directory
  [ds authorised-id directory-name hidden]
  (let [docker-path (-> (fetch-config) :docker-path)
        validated (validator/validate ContainerCreateSpec directory-name)
        path (str authorised-id "/" (:name validated) "-" authorised-id)
        project-dir (io/file docker-path path)]
    (if (.exists project-dir)
      (throw (ex-info "Bad request"
                      {:environment-name (:name validated) :message "This name already is used"}))
      (do
        (.mkdirs project-dir)
        (spit (io/file project-dir "docker-compose.yml") "# Docker Compose configuration")
        (json/write-str (create-environment ds authorised-id (:name validated) path hidden))))))

(defn run-docker-command
  [type project-dir & command]
  (let [command (into [project-dir "docker" type] command)
        result (apply execute-host-docker-command command)]
    (if (zero? (:exit result))
      (json/write-str {:status "success" :stdout (:out result) :stderr (:err result)})
      (throw (ex-info "Docker command failed"
                      {:command command
                       :stdout (:out result)
                       :stderr (:err result)
                       :exit-code (:exit result)})))))

(defn start-all
  [ds authorised-id environment-id]
  (log/info "Start all conatainers of" environment-id)
  (let [env (has-access-env? ds authorised-id environment-id)
        docker-path (-> (fetch-config) :docker-path)
        docker-host-path (-> (fetch-config) :docker-host-path)
        script-path (str docker-host-path "/check_containers.sh")
        project-dir (io/file docker-path (str (:path env)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (let [result (execute-host-docker-command docker-host-path "sh" script-path (str docker-host-path "/" (:path env)))]
        (if (zero? (:exit result))
          (->> (str docker-host-path "/" (:path env)) list-containers json/read-json
               (map #(->> (insert-or-update-container ds (:name %) (:status %) environment-id) first :id
                          (assoc % :id)))
               json/write-str)
          (json/write-str {:status "error"
                           :message (:out result)})))
      (throw (ex-info "Bad request" {:error "Directory not found"})))))

(defn stop-all
  [ds authorised-id environment-id]
  (let [env (has-access-env? ds authorised-id environment-id)
        docker-path (-> (fetch-config) :docker-path)
        docker-host-path (-> (fetch-config) :docker-host-path)
        project-dir (->> env :path str (io/file docker-path))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (run-docker-command "compose" (str docker-host-path "/" (:path env)) "stop")
      (throw (ex-info "Bad request" {:error "Project not found"})))))

(defn down-all
  [ds authorised-id environment-id]
  (let [env (has-access-env? ds authorised-id environment-id)
        docker-path (-> (fetch-config) :docker-path)
        docker-host-path (-> (fetch-config) :docker-host-path)
        project-dir (io/file docker-path (str (:path env)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (run-docker-command "compose" (str docker-host-path "/" (:path env)) "down")
      (throw (ex-info "Bad request" {:error "Project not found"})))))

(defn get-containers
  [ds authorised-id environment-id]
  (let [env (has-access-env? ds authorised-id environment-id)
        docker-path (-> (fetch-config) :docker-path)
        docker-host-path (-> (fetch-config) :docker-host-path)
        project-dir (io/file docker-path (str (:path env)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (->> (str docker-host-path "/" (:path env)) list-containers json/read-json
           (map #(->> (insert-or-update-container ds (:name %) (:status %) environment-id) first :id
                      (assoc % :id)))
           json/write-str)
      (throw (ex-info "Bad request" {:error "Not found"})))))

(defn handle-container
  [path container-name command]
  (let [docker-path (-> (fetch-config) :docker-path)
        docker-host-path (-> (fetch-config) :docker-host-path)
        project-dir (io/file docker-path (str path))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (run-docker-command "container" (str docker-host-path "/" path) command container-name)
      (throw (ex-info "Bad request" {:error "Directory not found"})))))

(def ContainerSearchSpec [:map
                          [:container_id :int]])

(defn stop-container
  [ds authorised-id environment-id data]
  (let [env (has-access-env? ds authorised-id environment-id)
        validated (validator/validate ContainerSearchSpec data)
        container (get-container-by-id ds (:container_id validated))]
    (if (empty? container)
      (throw (ex-info "Not found" {:errors "Container not found"}))
      (handle-container (:path env) (str (:name container)) "stop"))))

(defn start-container
  [ds authorised-id environment-id data]
  (let [env (has-access-env? ds authorised-id environment-id)
        validated (validator/validate ContainerSearchSpec data)
        container (get-container-by-id ds (:container_id validated))]
    (if (empty? container)
      (throw (ex-info "Not found" {:errors "Container not found"}))
      (handle-container (:path env) (str (:name container)) "start"))))

(def ComposeUpdateSpec [:map
                        [:data :string]])

(defn write-to-file
  [file-path content]
  (with-open [writer (io/writer file-path)]
    (.write writer ^String content)))

(defn read-from-file
  [file-path]
  (with-open [reader (io/reader file-path)]
    (let [buffered-reader (BufferedReader. reader)
          sb (StringBuilder.)]
      (loop [line (.readLine buffered-reader)]
        (if line
          (do
            (.append sb line)
            (.append sb "\n")
            (recur (.readLine buffered-reader)))
          (str sb))))))

(defn update-compose
  [ds authorised-id environment-id compose-update-data]
  (let [env (has-access-env? ds authorised-id environment-id)
        docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (:path env)))
        validated (validator/validate ComposeUpdateSpec compose-update-data)]
    (if validated
      (if (check-directory-existence (.getAbsolutePath project-dir))
        (do
          (write-to-file (str (.getAbsolutePath project-dir) "/docker-compose.yml") (:data validated))
          (json/write-str {:success "true"}))
        (throw (ex-info "Bad request" {:error "Directory not found"})))
      (throw (ex-info "Bad request" {:error "Invalid data provided"})))))

(defn get-compose
  [ds authorised-id environment-id]
  (let [env (has-access-env? ds authorised-id environment-id)
        docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (:path env)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (let [compose (read-from-file (str (.getAbsolutePath project-dir) "/docker-compose.yml"))]
        (json/write-str {:compose compose}))
      (throw (ex-info "Bad request" {:error "Directory not found"})))))

(def LogSizeSpec [:map
                  [:container_id :int]
                  [:size :string]])

(defn get-container-log
  [ds authorised-id environment-id log-size]
  (let [env (has-access-env? ds authorised-id environment-id)
        docker-path (-> (fetch-config) :docker-path)
        docker-host-path (-> (fetch-config) :docker-host-path)
        project-dir (io/file docker-path (str (:path env)))
        validated (validator/validate LogSizeSpec log-size)]
    (if validated
      (let [container (get-container-by-id ds (:container_id validated))]
        (if (empty? container)
          (throw (ex-info "Not found" {:errors "Container not found"}))
          (if (check-directory-existence (.getAbsolutePath project-dir))
            (run-docker-command "container" (str docker-host-path "/" (:path env)) "logs" (str "--tail=" (:size validated)) (:name container))
            (throw (ex-info "Bad request" {:error "Directory not found"})))))
      (throw (ex-info "Bad request" {:error "Invalid data provided"})))))

(defn get-all-environments
  [ds authorised-id]
  (json/write-str (get-all-user-environment ds authorised-id)))

(defn generate-compose-hidden
  [user-id compose-update-data name]
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str user-id "/" (:name name) "-" user-id))
        validated (validator/validate ComposeUpdateSpec compose-update-data)]
    (if validated
      (if (check-directory-existence (.getAbsolutePath project-dir))
        (do
          (write-to-file (str (.getAbsolutePath project-dir) "/docker-compose.yml") (:data validated))
          (json/write-str {:success "true"}))
        (throw (ex-info "Bad request" {:error "Directory not found"})))
      (throw (ex-info "Bad request" {:error "Invalid data provided"})))))

(defn generate-docker-hidden
  [ds authorised-id]
  (let [name {:name (str "hidden-" (System/currentTimeMillis))}]
    (generate-docker-directory ds authorised-id name true)
    (generate-compose-hidden authorised-id {:data "ABOBA"} name)))

(defn get-configurations
  [ds]
  (json/write-str (get-all-configurations ds)))

(defn get-configuration-by-id
  [ds configuration-id]
  (json/write-str (get-configuration ds configuration-id)))

(defn copy-dir
  [source-path target-path dir]
  (log/info "Copy" (str source-path "/") target-path dir)
  (sh "cp" "-r" (str source-path "/") target-path :dir dir))

(defn create-env-by-server-made-configuration
  [ds authorised-id configuration-id environment-name]
  (let [docker-path (-> (fetch-config) :docker-path)
        validated (validator/validate ContainerCreateSpec environment-name)
        configuration (get-configuration-full ds configuration-id)
        path (str authorised-id "/" (:name validated) "-" authorised-id)
        project-dir (io/file docker-path path)
        path (str path "/" (-> configuration :path (str/split #"/") last))
        configuration-dir (io/file (:path configuration))]
    (log/info (-> configuration :path (str/split #"/")))
    (if (.exists project-dir)
      (throw (ex-info "Bad request"
                      {:environment-name (:name validated) :message "This name already in use"}))
      (do
        (.mkdirs project-dir)
        (copy-dir (.getAbsolutePath configuration-dir) (.getAbsolutePath project-dir) (.getAbsolutePath configuration-dir))
        (json/write-str (create-environment ds authorised-id (:name validated) path false))))))

(def ExeFileSpec [:map
                  [:configuration_id :int]
                  [:files [:sequential [:map
                                        [:name :string]
                                        [:extension :string]
                                        [:file :string]]]]])

(defn get-file-by-name [files file-name]
  (let [item (filter #(= (:name %) (name file-name)) files)]
    (if (seq item)
      (first item)
      nil)))

(defn process-mapping-entry [env-path entry files]
  (let [file (get-file-by-name files (first entry))
        path (str env-path "/" (-> entry second :path))]
    (log/info file path entry)
    (when (some? file)
      (f/save-base64-file-custom-prefix (:file file) path))))

(defn save-by-mapping [env-path mapping files]
  (->> mapping
       json/read-json
       (map #(process-mapping-entry env-path % files))))

(defn environment-upload
  [ds authorised-id environment-id file-data]
  (let [docker-path (-> (fetch-config) :docker-path)
        env (has-access-env? ds authorised-id environment-id)
        validated (validator/validate ExeFileSpec file-data)
        configuration (get-configuration ds (:configuration_id validated))
        env-path (str docker-path "/" (:path env))]
    (log/info env-path)
    (log/info configuration)
    (log/info env)
    (log/info file-data)
    (save-by-mapping env-path (:mapping configuration) (:files file-data))))
