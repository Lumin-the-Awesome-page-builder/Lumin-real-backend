(ns utils.jwt
  (:require [buddy.sign.jwt :as jwt]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [components.config :refer [fetch-config]]))

(defn extract-bearer [headers authorization-key]
  (try
    (-> headers
        (get authorization-key)
        (str/split #"\s")
        (second))
    (catch Exception ex
      (log/info "Bearer extracting failed due to: " ex)
      nil)))

(defn unsign [token]
  (try
    (let [secret (-> (fetch-config) (:jwt-secret))]
      (select-keys (jwt/unsign token secret) [:sub]))
    (catch Exception ex
      (log/info "Unsigning failed due to: " ex)
      nil)))