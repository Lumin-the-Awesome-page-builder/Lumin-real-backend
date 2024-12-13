(ns components.ring-server.middleware
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [ring.util.response :as response]
            [utils.jwt :refer [extract-bearer unsign]]))

(defn wrap-deps
  [handler deps]
  (fn [request]
    (handler (assoc request :deps deps))))

(defn wrap-deps-ws [deps]
  (fn [next request]
    (next (assoc request :deps deps))))

(defn wrap-jwt-guard
  [handler excluded]
  (fn [request]
    (if (or (= (:request-method request) :options) (seq (filter #(re-matches % (:uri request)) excluded)))
      (handler (assoc request :authorized {:sub 1}))
      (let [{:keys [headers]} request
            authorized (-> headers (extract-bearer "authorization") (unsign))]
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
    "Internal server error" (into {:status 500} (ex-data ex))
    "Bad request" (into {:status 400} (ex-data ex))
    "Not found" (into {:status 404} (ex-data ex))
    {:status 500 :error "Internal server error"}))

(defn wrap-exceptions-handling
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception ex
        (log/error "Uncaught exception: " (.getMessage ex) ex)
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

(defn wrap-on [handler regex middleware & args]
  (fn [request]
    (if (seq (filter #(re-matches % (:uri request)) regex))
      (apply middleware (concat [handler] args))
      (handler request))))
