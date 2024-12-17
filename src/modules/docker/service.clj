(ns modules.docker.service
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [components.config :refer [fetch-config]]
            [clojure.java.shell :refer [sh]]
            [clojure.data.json :as json]
            [modules.docker.model :refer [get-environment-by-id create-environment get-container-by-id
                                          insert-or-update-container get-all-user-environment get-all-configurations
                                          get-configuration]]
            [utils.validator :as validator])
  (:import (java.io BufferedReader)))

(defn check-directory-existence [path]
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
      (throw (ex-info "Not found" {})))))

(defn list-containers [dir]
  (let [{:keys [out exit]} (sh "docker-compose" "ps" "--format" "{{.Names}} {{.Status}}" :dir dir)]
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
  [type command project-dir]
  (let [cmd (str "docker " type " " command)
        result (sh "sh" "-c" cmd :dir project-dir)]
    (if (zero? (:exit result))
      (json/write-str {:status "success" :stdout (:out result) :stderr (:err result)})
      (throw (ex-info "Docker command failed"
                      {:command command
                       :stdout (:out result)
                       :stderr (:err result)
                       :exit-code (:exit result)})))))

(defn start-all
  [ds authorised-id environment-id]
  (let [env (has-access-env? ds authorised-id environment-id)
        docker-path (-> (fetch-config) :docker-path)
        script-path (str docker-path "/check_containers.sh")
        project-dir (io/file docker-path (str (:path env)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (let [result (sh "sh" script-path (.getAbsolutePath project-dir))]
        (if (zero? (:exit result))
          (->> project-dir .getAbsolutePath list-containers json/read-json
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
        project-dir (->> env :path str (io/file docker-path))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (run-docker-command  "compose" "stop" (.getAbsolutePath project-dir))
      (throw (ex-info "Bad request" {:error "Project not found"})))))

(defn down-all
  [ds authorised-id environment-id]
  (let [env (has-access-env? ds authorised-id environment-id)
        docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (:path env)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (run-docker-command "compose" "down" (.getAbsolutePath project-dir))
      (throw (ex-info "Bad request" {:error "Project not found"})))))

(defn get-containers
  [ds authorised-id environment-id]
  (let [env (has-access-env? ds authorised-id environment-id)
        docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (:path env)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (->> project-dir .getAbsolutePath list-containers json/read-json
           (map #(->> (insert-or-update-container ds (:name %) (:status %) environment-id) first :id
                      (assoc % :id)))
           json/write-str)
      (throw (ex-info "Bad request" {:error "Not found"})))))

(defn handle-container
  [path container-name command]
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str path))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (run-docker-command "container" (str command " " container-name) (.getAbsolutePath project-dir))
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
        project-dir (io/file docker-path (str (:path env)))
        validated (validator/validate LogSizeSpec log-size)]
    (if validated
      (let [container (get-container-by-id ds (:container_id validated))]
        (if (empty? container)
          (throw (ex-info "Not found" {:errors "Container not found"}))
          (if (check-directory-existence (.getAbsolutePath project-dir))
            (run-docker-command "container" (str  "logs --tail=" (:size validated) " " :name container) (.getAbsolutePath project-dir))
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