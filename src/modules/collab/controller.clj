(ns modules.collab.controller
  (:require [ring.websocket :as ws]))

(def ws-routes
  {"auth" (fn [request]
            (let [{:keys [clients socket]} request]
              (swap! clients assoc (str (-> request :authorized :sub) (System/currentTimeMillis)) socket)
              {:status 200 :data "authorized"}))
   "get-authorized" (fn [request]
                      (let [{:keys [authorized]} request]
                        {:status 200 :data authorized}))
   "ping-all" (fn [request]
                (let [{:keys [clients]} request]
                  (doseq [client @clients]
                    (ws/send (second client) "ping"))
                  {:status 200 :data nil}))})