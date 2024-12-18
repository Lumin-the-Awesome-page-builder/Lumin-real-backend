(ns utils.file
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [components.config :refer [fetch-config]])
  (:import (java.util Base64)))

(defn prefixed-path [name]
  (-> (fetch-config)
      :file-path
      (str "/" name)))

(defn save-base64-file [base64-str file-name]
  (try
    (println (prefixed-path file-name))
    (let [decoder (Base64/getDecoder)
          bytes (.decode decoder base64-str)]
      (with-open [os (io/output-stream (prefixed-path file-name))]
        (.write os bytes)))
    (catch Exception e
      (log/warn "File save failed due to exception: " (ex-message e))
      (throw (ex-info "Bad request" {:error "Bad base64"})))))

(defn save-base64-file-custom-prefix [base64-str file-name]
  (try
    (let [decoder (Base64/getDecoder)
          bytes (.decode decoder base64-str)]
      (with-open [os (io/output-stream file-name)]
        (.write os bytes)))
    (catch Exception e
      (log/warn "File save failed due to exception: " (ex-message e))
      (throw (ex-info "Bad request" {:error "Bad base64"})))))

(defn drop-file [file-name]
  (io/delete-file (prefixed-path file-name) :not-found))

(defn get-stream [file-name]
  (when (str/includes? file-name "..")
    (throw (ex-info "Bad request" {:error "Bad path provided"})))
  (io/file (prefixed-path file-name)))
