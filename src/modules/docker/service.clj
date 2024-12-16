(ns modules.docker.service
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [components.config :refer [fetch-config]]
            [clojure.java.shell :refer [sh]]
            [modules.project.model :refer [get-project]]
            [clojure.data.json :as json]
            [utils.validator :as validator]))

(defn file-exists?
  [path]
  (let [file (io/file path)]
    (.exists file)))

(defn check-directory-existence
  [path]
  (if (not (file-exists? path))
    false
    true))

(defn- has-access?
  [ds user-id project-id]
  (let [project (get-project ds project-id)]
    (if (and project (= (:owner_id project) user-id))
      true
      (println project ds project-id user-id))))


(defn generate-docker-directory
  [project-id]
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str project-id))]
    (if (.exists project-dir)
      (throw (ex-info (str "Directory already exists: " project-dir)
                      {:project-id project-id :path (.getPath project-dir)}))
      (do
        (.mkdirs project-dir)
        (spit (io/file project-dir "docker-compose.yml") "# Docker Compose configuration")))))

(defn run-docker-command
  [command project-dir]
  (let [cmd (str "docker compose " command)]
    (let [result (sh "sh" "-c" cmd :dir project-dir)] ;; Запуск команды в директории проекта
      (if (zero? (:exit result))
        (json/write-str {:status "success" :stdout (:out result) :stderr (:err result)})
        (throw (ex-info "Docker command failed"
                        {:command command
                         :stdout (:out result)
                         :stderr (:err result)
                         :exit-code (:exit result)}))))))

  (defn start-all
    [ds authorised-id project-id]
    (has-access? ds authorised-id project-id)
    (let [docker-path (-> (fetch-config) :docker-path)
          script-path (str docker-path "/check_containers.sh")
          project-dir (io/file docker-path (str project-id))]
      (if (check-directory-existence (.getAbsolutePath project-dir))
        (let [result (sh "sh" script-path (.getAbsolutePath project-dir))]
          (if (zero? (:exit result))
            (json/write-str {:status "success"
                             :message (:logs result)})
            (json/write-str {:status "error"
                             :message (:logs result)})))
        (throw (ex-info "Bad request" {:error "Project not found"})))))

  (defn stop-all
    [ds authorised-id project-id]
    (has-access? ds authorised-id project-id)
    (let [docker-path (-> (fetch-config) :docker-path)]
      (let [project-dir (io/file docker-path (str project-id))]
        (if (check-directory-existence (.getAbsolutePath project-dir))
          (run-docker-command "stop" (.getAbsolutePath project-dir))
          (throw (ex-info "Bad request" {:error "Project not found"}))))))

(defn down-all
  [ds authorised-id project-id]
  (has-access? ds authorised-id project-id)
  (let [docker-path (-> (fetch-config) :docker-path)]
    (let [project-dir (io/file docker-path (str project-id))]
      (if (check-directory-existence (.getAbsolutePath project-dir))
        (run-docker-command "down" (.getAbsolutePath project-dir))
        (throw (ex-info "Bad request" {:error "Project not found"}))))))

(defn list-containers [dir]
  (let [{:keys [out exit]} (sh "docker-compose" "ps" "--format" "{{.Names}} {{.Status}}" :dir dir)]
    (if (= exit 0)
      (json/write-str
        (map #(let [[name & status] (str/split % #" " 2)]
                {:name name, :status (str/join " " status)})
             (str/split-lines out)))
      (throw (ex-info "Error executing docker command" {:error "Not found"})))))

(defn get-containers
  [ds authorised-id project-id]
  (has-access? ds authorised-id project-id)
  (let [docker-path (-> (fetch-config) :docker-path)]
    (let [project-dir (io/file docker-path (str project-id))]
      (if (check-directory-existence (.getAbsolutePath project-dir))
        (list-containers (.getAbsolutePath project-dir))
        (throw (ex-info "Bad request" {:error "Not found"}))))))

(def ContainerSearchSpec [:map
                          [:name :string]])

(defn handle-service
  [ds authorised-id project-id service-name command]
  (println service-name)
  (has-access? ds authorised-id project-id)
  (println service-name)
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str project-id))
        validated (validator/validate ContainerSearchSpec service-name)]
    (if-not validated
      (throw (ex-info "Bad request" {:error "Invalid service name"})))
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (run-docker-command (str command " " (:name validated)) (.getAbsolutePath project-dir))
      (throw (ex-info "Bad request" {:error "Directory not found"})))))

(defn stop-service [ds authorised-id project-id service-name]
  (handle-service ds authorised-id project-id service-name "stop"))

(defn down-service [ds authorised-id project-id service-name]
  (handle-service ds authorised-id project-id service-name "down"))

(defn start-service [ds authorised-id project-id service-name]
  (handle-service ds authorised-id project-id service-name "start"))