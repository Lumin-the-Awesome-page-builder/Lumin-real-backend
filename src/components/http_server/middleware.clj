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
  (try
    (-> headers
        (get "authorization")
        (str/split #"\s")
        (second))
    (catch Exception ex
      (log/info "Bearer extracting failed due to: " ex)
      nil)))

(defn- unsign [token]
  (try
    (let [secret (-> (fetch-config) (:jwt-secret))]
      (select-keys (jwt/unsign token secret) [:sub]))
    (catch Exception ex
      (log/info "Unsigning failed due to: " ex)
      nil)))

(defn wrap-jwt-guard
  [handler excluded]
  (fn [request]
    (if (or (= (:request-method request) :options) (seq (filter #(re-matches % (:uri request)) excluded)))
      (handler (assoc request :authorized {:sub 11}))
      (let [{:keys [headers]} request
            authorized (-> headers (extract-bearer) (unsign))]
        (if authorized
          (do
            (log/info "Authorized user: " authorized)
            (handler (assoc request :authorized authorized)))
          {:status 403})))))

(defn wrap-request-logging
  [handler]
  (fn [request]
    (log/info "New incoming request" (:request-method request) (:uri request) (:params request) (:headers request))
    (let [response (handler request)]
      (log/info "On response: ")
      (log/info "Status: " (:status response))
      (log/info "Headers: " (:headers response))
      (log/info "Body: " (:body response))
      response)))

(defn- parse-ex [ex]
  (case (.getMessage ex)
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
          (log/error "On return: " ex)
          (-> (response/response (json/write-str ex))
              (response/header "Content-Type" "application/json")
              (response/status (:status ex))))))))

(defn wrap-content-type-json
  [handler]
  (fn  [request]
    (let [response (handler request)]
      (if (empty? (:headers response))
        (-> response
            (response/header "Content-Type" "application/json"))
        response))))

(defn wrap-not-found
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (:status response)
        response
        {:status 404}))))
