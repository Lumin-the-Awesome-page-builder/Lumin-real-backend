(ns modules.editor.controller
  (:require [ring.websocket :as ws]
            [modules.editor.service :refer [start-collaboration edit patch-tree block-element release-element save-project close-edit]]))

(def ws-routes
  {"auth"
   (fn [request]
     (let [{:keys [clients socket]} request]
       (swap! clients assoc (str (-> request :authorized :sub) (System/currentTimeMillis)) socket)
       {:status 200 :data "authorized"}))

   "edit"
   (fn [request]
     (let [{:keys [authorized data]} request
           {:keys [redis datasource]} (:deps request)]
       (edit redis datasource (:sub authorized) (:project_id data))))

   "edit-by-key"
   (fn [request]
     (let [{:keys [authorized data]} request
           {:keys [redis datasource]} (:deps request)]
       (start-collaboration redis datasource (:sub authorized) (:key data))))

   "patch-tree"
   (fn [request]
     (let [{:keys [data clients authorized]} request
           {:keys [redis]} (:deps request)]
       (patch-tree redis (:patch-data data) (:sub authorized) clients)))

   "block-element"
   (fn [request]
     (let [{:keys [data clients authorized]} request
           {:keys [redis]} (:deps request)]
       (block-element redis data (:sub authorized) clients)))

   "release-element"
   (fn [request]
     (let [{:keys [data clients authorized]} request
           {:keys [redis]} (:deps request)]
       (release-element redis data (:sub authorized) clients)))

   "save"
   (fn [request]
     (let [{:keys [data]} request
           {:keys [redis datasource]} (:deps request)]
       (save-project redis datasource data)))

   "close"
   (fn [request]
     (let [{:keys [data authorized]} request
           {:keys [redis datasource]} (:deps request)]
       (close-edit redis datasource data (:sub authorized))))

   "ping-all"
   (fn [request]
     (let [{:keys [clients]} request]
       (doseq [client @clients]
         (ws/send (second client) "ping"))
       {:status 200 :data nil}))})