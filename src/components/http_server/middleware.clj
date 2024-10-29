(ns components.http-server.middleware
  (:require [buddy.sign.jwt :as jwt]
            [clojure.tools.logging :as log]
            [components.config :refer [fetch-config]]
            [clojure.string :as str]
            [ring.util.response :as response]))

(defn wrap-deps
  [handler deps]
  (fn [request]
    (handler (assoc request :deps deps))))

(defn- extract-bearer [headers]
  (-> headers
      (get "authorization")
      (str/split #"\s")
      (second)))

(defn- unsign [token]
  (let [secret (-> (fetch-config) (:jwt-secret))]
    (select-keys (jwt/unsign token secret) [:sub])))

(defn wrap-jwt-guard
  [handler]
  (fn [request]
    (let [{:keys [headers]} request
          token (extract-bearer headers)]
      (try
        (handler (assoc request :authorized (unsign token)))
        (catch RuntimeException _
          {:status 403})))))

(defn wrap-request-logging
  [handler]
  (fn [request]
    (log/info "New incoming request" (:url request) (:params request) (:headers request))
    (handler request)))

(defn wrap-exceptions-handling
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/error "Uncaught exception: " (.getMessage e))
        (-> (response/response {:status 500 :error (.getMessage e)})
            (response/status 500))))))