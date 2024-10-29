(ns components.http-server.middleware
  (:require [buddy.sign.jwt :as jwt]
            [components.config :refer [fetch-config]]
            [clojure.string :as str]))

(defn wrap-deps
  [handler deps]
  (fn [request]
    (handler (assoc request :deps deps))))

(defn- extract-bearer [headers]
  (-> headers
      (:Authorization)
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