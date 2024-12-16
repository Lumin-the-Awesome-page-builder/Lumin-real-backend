(ns utils.ws
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET]]
            [ring.websocket :as ws]
            [utils.jwt :refer [extract-bearer unsign]]))

(defn- on-open [socket]
  (log/info "Socket open: " socket))

(defn- parse-message [message]
  (let [parsed (json/read-json message true)]
    (log/info parsed)
    (if (nil? (:headers parsed))
      {:route ""}
      parsed)))

(defn- response
  ([status data]
   {:status status :data data})

  ([status data awaited awaited_type]
   (if awaited
     (assoc (response status data) :awaited_type awaited_type)
     (response status data))))

(defn wrap-jwt-auth [excluded]
  (fn [next request]
    (if (seq (filter #(re-matches % (:route request)) excluded))
      (next (assoc request :authorized {:sub 1}))
      (if-let [authorized (-> request :headers (extract-bearer :Authorization) (unsign))]
        (next (assoc request :authorized authorized))
        (response 403 :nil)))))

(defn wrap-exception-handling []
  (fn [next request]
    (try
      (next request)
      (catch Exception ex
        (log/info ex)
        (response 500 (.getMessage ex))))))

(defn- apply-middlewares [middlewares handler request]
  (if (zero? (count middlewares))
    (handler request)
    (let [middleware (first middlewares)
          middlewares (drop 1 middlewares)
          next (fn [request]
                 (apply-middlewares middlewares handler request))]
      (middleware next request))))

(def clients (atom {}))

(defn- message-handler [socket message routes middlewares]
  (let [parsed (parse-message message)
        request {:socket socket
                 :headers (:headers parsed)
                 :data (:data parsed)
                 :clients clients
                 :route (:route parsed)}
        awaited? (-> request :headers :awaited)
        awaited_type (-> request :headers :awaited_type)]
    (if-let [handler (get routes (:route request))]
      (let [response (->> request
                          (apply-middlewares middlewares handler))]
        (->> (if awaited?
               (assoc response :awaited_type awaited_type)
               response)
             (json/write-str)
             (ws/send socket)))
      (->> (response 404 :nil awaited? awaited_type)
           (json/write-str)
           (ws/send socket)))))

(defn create-ws-endpoint [route routes middlewares on-close]
  (GET route request
    (when (ws/upgrade-request? request)
      {::ws/listener
       {:on-open
        (fn [socket]
          (on-open socket))
        :on-message
        (fn [socket message]
          (message-handler socket message routes middlewares))
        :on-close (fn [& args] (on-close clients args))}})))