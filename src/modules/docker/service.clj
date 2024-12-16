(ns modules.docker.service
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [components.config :refer [fetch-config]]
            [clojure.java.shell :refer [sh]]
            [clojure.data.json :as json]
            [utils.validator :as validator])
  (:import (java.io BufferedReader)))

(defn file-exists?
  [path]
  (let [file (io/file path)]
    (.exists file)))

(defn check-directory-existence
  [path]
  (if (not (file-exists? path))
    false
    true))

(def ContainerCreateSpec [:map
                          [:name :string]])

(defn generate-docker-directory
  [authorised-id directory-name]
  (let [docker-path (-> (fetch-config) :docker-path)
        validated (validator/validate ContainerCreateSpec directory-name)
        project-dir (io/file docker-path (str (str authorised-id) "/" (:name validated)))]
    (if (.exists project-dir)
      (throw (ex-info "Bad request"
                      {:project-name (:name validated) :message "This name already is used"}))
      (do
        (.mkdirs project-dir)
        (spit (io/file project-dir "docker-compose.yml") "# Docker Compose configuration")))))

(defn run-docker-command
  [command project-dir]
  (let [cmd (str "docker compose " command)
        result (sh "sh" "-c" cmd :dir project-dir)]
    (if (zero? (:exit result))
      (json/write-str {:status "success" :stdout (:out result) :stderr (:err result)})
      (throw (ex-info "Docker command failed"
                      {:command command
                       :stdout (:out result)
                       :stderr (:err result)
                       :exit-code (:exit result)})))))

(defn start-all
  [authorised-id environment-name]
  (let [docker-path (-> (fetch-config) :docker-path)
        script-path (str docker-path "/check_containers.sh")
        project-dir (io/file docker-path (str (str authorised-id) "/" (str environment-name)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (let [result (sh "sh" script-path (.getAbsolutePath project-dir))]
        (if (zero? (:exit result))
          (json/write-str {:status "success"
                           :message (:logs result)})
          (json/write-str {:status "error"
                           :message (:logs result)})))
      (throw (ex-info "Bad request" {:error "Directory not found"})))))

(defn stop-all
  [authorised-id environment-name]
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (str authorised-id) "/" (str environment-name)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (run-docker-command "stop" (.getAbsolutePath project-dir))
      (throw (ex-info "Bad request" {:error "Project not found"})))))

(defn down-all
  [authorised-id environment-name]
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (str authorised-id) "/" (str environment-name)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (run-docker-command "down" (.getAbsolutePath project-dir))
      (throw (ex-info "Bad request" {:error "Project not found"})))))

(defn list-containers [dir]
  (let [{:keys [out exit]} (sh "docker-compose" "ps" "--format" "{{.Names}} {{.Status}}" :dir dir)]
    (if (= exit 0)
      (json/write-str
       (map #(let [[name & status] (str/split % #" " 2)]
               {:name name, :status (str/join " " status)})
            (str/split-lines out)))
      (throw (ex-info "Error executing docker command" {:error "Not found"})))))

(defn get-containers
  [authorised-id environment-name]
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (str authorised-id) "/" (str environment-name)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (list-containers (.getAbsolutePath project-dir))
      (throw (ex-info "Bad request" {:error "Not found"})))))

(def ContainerSearchSpec [:map
                          [:name :string]])

(defn handle-service
  [authorised-id environment-name service-name command]
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (str authorised-id) "/" (str environment-name)))
        validated (validator/validate ContainerSearchSpec service-name)]
    (if validated
      (if (check-directory-existence (.getAbsolutePath project-dir))
        (run-docker-command (str command " " (:name validated)) (.getAbsolutePath project-dir))
        (throw (ex-info "Bad request" {:error "Directory not found"})))
      (throw (ex-info "Bad request" {:error "Invalid service name"})))))

(defn stop-service
  [authorised-id environment-name service-name]
  (handle-service authorised-id environment-name service-name "stop"))

(defn down-service
  [authorised-id environment-name service-name]
  (handle-service authorised-id environment-name service-name "down"))

(defn start-service
  [authorised-id environment-name service-name]
  (handle-service authorised-id environment-name service-name "start"))

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
  [authorised-id environment-name path-data]
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (str authorised-id) "/" (str environment-name)))
        validated (validator/validate ComposeUpdateSpec path-data)]
    (if validated
      (if (check-directory-existence (.getAbsolutePath project-dir))
        (write-to-file (str (.getAbsolutePath project-dir) "/docker-compose.yml") (:data validated))
        (throw (ex-info "Bad request" {:error "Directory not found"})))
      (throw (ex-info "Bad request" {:error "Invalid data provided"})))))

(defn get-compose
  [authorised-id environment-name]
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (str authorised-id) "/" (str environment-name)))]
    (if (check-directory-existence (.getAbsolutePath project-dir))
      (read-from-file (str (.getAbsolutePath project-dir) "/docker-compose.yml"))
      (throw (ex-info "Bad request" {:error "Directory not found"})))))

(def LogSizeSpec [:map
                  [:name :string]
                  [:size :string]])

(defn get-service-log
  [authorised-id environment-name log-size]
  (let [docker-path (-> (fetch-config) :docker-path)
        project-dir (io/file docker-path (str (str authorised-id) "/" (str environment-name)))
        validated (validator/validate LogSizeSpec log-size)]
    (if validated
      (if (check-directory-existence (.getAbsolutePath project-dir))
        (run-docker-command (str  "logs --tail=" (:size validated) " " (:name validated)) (.getAbsolutePath project-dir))
        (throw (ex-info "Bad request" {:error "Directory not found"})))
      (throw (ex-info "Bad request" {:error "Invalid data provided"})))))