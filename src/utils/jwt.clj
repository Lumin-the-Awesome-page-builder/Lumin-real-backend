(ns utils.jwt
  (:require [buddy.sign.jwt :as jwt]
            [buddy.sign.jws :as jws]
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
    (let [secret (-> (fetch-config) :jwt-secret)]
      (select-keys (jwt/unsign token secret) [:sub]))
    (catch Exception ex
      (log/info "Unsigning failed due to: " ex)
      nil)))

; Available sign opts: [:exp :nbf :iat :iss :aud]
(defn sign
  "expire-time: time in millis"
  [data expire-time]
  (try
    (let [secret (-> (fetch-config) :jwt-secret)
          token (jwt/sign data secret {:iat (System/currentTimeMillis) :exp (+ expire-time (System/currentTimeMillis))})]
      token)
    (catch Exception ex
      (log/info "Signing failed due to: " ex)
      nil)))

(defn encrypt [data]
  (let [secret (-> (fetch-config) :jwt-secret)
        encrypted (jws/sign data secret)]
    encrypted))

(defn validate [secret]
  (jws/unsign secret (-> (fetch-config) :jwt-secret)))