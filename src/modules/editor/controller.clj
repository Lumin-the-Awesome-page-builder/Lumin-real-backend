(ns modules.editor.controller
  (:require [ring.websocket :as ws]
            [modules.editor.service :refer [auth-client patch-tree update-prop block-element release-element save-project close-edit remove-element patch-item-ordering]]))

(def ws-routes
  {"auth"
   (fn [request]
     (let [{:keys [data clients socket authorized]} request
           {:keys [redis]} (:deps request)]
       (auth-client redis clients data (:sub authorized) socket)))

   "patch-tree"
   (fn [request]
     (let [{:keys [data clients authorized]} request
           {:keys [redis]} (:deps request)]
       (patch-tree redis data (:sub authorized) clients)))

   "patch-prop"
   (fn [request]
     (let [{:keys [data clients authorized]} request
           {:keys [redis]} (:deps request)]
       (update-prop redis data (:sub authorized) clients)))

   "patch-item-ordering"
   (fn [request]
     (let [{:keys [data clients authorized]} request
           {:keys [redis]} (:deps request)]
       (patch-item-ordering redis data (:sub authorized) clients)))

   "remove-element"
   (fn [request]
     (let [{:keys [data clients authorized]} request
           {:keys [redis]} (:deps request)]
       (remove-element redis data (:sub authorized) clients)))

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
           {:keys [redis]} (:deps request)]
       (close-edit redis data (:sub authorized))))

   "ping-all"
   (fn [request]
     (let [{:keys [clients]} request]
       (doseq [client @clients]
         (ws/send (second client) "ping"))
       {:status 200 :data nil}))})