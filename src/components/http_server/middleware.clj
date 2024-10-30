(ns components.http-server.middleware
  (:require [buddy.sign.jwt :as jwt]
            [clojure.data.json :as json]
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
    (try
      (let [{:keys [headers]} request
            token (extract-bearer headers)]
        (handler (assoc request :authorized (unsign token))))
      (catch RuntimeException _
        {:status 403}))))

(defn wrap-request-logging
  [handler]
  (fn [request]
    (log/info "New incoming request" (:request-method request) (:uri request) (:params request) (:headers request))
    (handler request)))

(defn- parse-ex [ex]
  (case (.getMessage ex)
    "Forbidden" {:status 403 :error ""}
    "Bad request" (into {:status 400} (ex-data ex))
    "Not found" (into {:status 404} (ex-data ex))
    {:status 500 :error "Internal server error"}))

(defn wrap-exceptions-handling
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception ex
        (log/error "Uncaught exception: " (.getMessage ex))
        (let [ex (parse-ex ex)]
          (-> (response/response (json/write-str ex))
              (response/header "Content-Type" "application/json")
              (response/status (:status ex))))))))

(defn wrap-content-type-json
  [handler]
  (fn  [request]
    (response/header (handler request) "Content-Type" "application/json")))