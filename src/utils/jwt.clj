(ns utils.jwt
  (:require [buddy.sign.jwt :as jwt]
            [buddy.sign.jws :as jws]
            [clojure.data.json :as json]
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

(defn unsign
  ([token] (unsign token [:sub]))
  ([token keys]
   (try
     (let [secret (-> (fetch-config) :jwt-secret)]
       (log/info token secret)
       (select-keys (jwt/unsign token secret) keys))
     (catch Exception ex
       (log/info "Unsigning failed due to: " ex)
       nil))))

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

(defn encrypt [data secret]
  (let [encrypted (jws/sign data secret)]
    encrypted))

(defn validate [hash secret]
  (-> hash
      (jws/unsign secret)
      slurp
      (json/read-json)))