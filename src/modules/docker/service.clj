(ns modules.docker.service
  (:require [clojure.java.io :as io]
            [components.config :refer [fetch-config]]
            [clojure.java.shell :refer [sh]]
            [modules.project.service :refer [get-by-id]]
            [clojure.data.json :as json]))

(defn file-exists?
  [path]
  (let [file (io/file path)]
    (.exists file)))

(defn check-directory-existence
  [path]
  (if (not (file-exists? path))
    false
    true))


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
  (let [cmd (str "docker-compose " command)]
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
    (let [docker-path (-> (fetch-config) :docker-path)
          script-path (str docker-path "/check_containers.sh")
          project-dir (io/file docker-path (str project-id))]
      (if (check-directory-existence (.getAbsolutePath project-dir))
        (let [result (sh "sh" script-path (.getAbsolutePath project-dir))]
          (if (zero? (:exit result))
            (json/write-str {:status "success"
                             :message (:out result)})
            (json/write-str {:status "error"
                             :message (:err result)})))
        (throw (ex-info "Bad request" {:error "Project not found"})))))

  (defn stop-all
    [ds authorised-id project-id]
    (let [docker-path (-> (fetch-config) :docker-path)]
      (let [project-dir (io/file docker-path (str project-id))]
        (if (check-directory-existence (.getAbsolutePath project-dir))
          (run-docker-command "stop" (.getAbsolutePath project-dir))
          (throw (ex-info "Bad request" {:error "Project not found"}))))))

(defn down-all
  [ds authorised-id project-id]
  (let [docker-path (-> (fetch-config) :docker-path)]
    (let [project-dir (io/file docker-path (str project-id))]
      (if (check-directory-existence (.getAbsolutePath project-dir))
        (run-docker-command "down" (.getAbsolutePath project-dir))
        (throw (ex-info "Bad request" {:error "Project not found"}))))))