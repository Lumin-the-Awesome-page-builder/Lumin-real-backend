(ns components.ring-server.router
  (:require [compojure.core :refer [routes]]
            [modules.docker.controller :as docker]
            [modules.library.controller :as library]
            [modules.file.controller :as file]
            [modules.widget.controller :as widget]
            [modules.project.controller :as project]
            [modules.nginx.controller :as nginx]
            [modules.forms.controller :as form]
            [modules.editor.controller :as collab]
            [modules.media.controller :as media]
            [modules.editor.service :as collab-service]
            [utils.ws :as ws]
            [components.ring-server.middleware :as middlewares]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.cors :refer [wrap-cors]]
            [iapetos.core :as prometheus]
            [iapetos.collector.ring :as prometheus-ring]))

(defonce registry
  (-> (prometheus/collector-registry)
      (prometheus-ring/initialize)))

(def auth-excluded
  [#"\/lumin\/file\/.*" #"\/lumin\/metrics" #"\/lumin\/collab\/ws" #"\/lumin\/form-handler\/.*"])

(defn app-routes [component]
  (-> routes
      (apply (concat '() (library/routes)
                     (project/routes)
                     (widget/routes)
                     (file/routes)
                     (docker/routes)
                     (form/routes)
                     (nginx/routes)
                     (media/routes)
                     [(ws/create-ws-endpoint "/lumin/collab/ws" ;Endpoint
                                             collab/ws-routes ;Router
                                             [(ws/wrap-exception-handling)
                                              (ws/wrap-jwt-auth auth-excluded) ;Middlewares
                                              (middlewares/wrap-deps-ws component)]
                                             (fn [clients args]
                                               (collab-service/on-close clients
                                                                        (-> component :redis :redis)
                                                                        (-> component :datasource)
                                                                        args)))]))
      (middlewares/wrap-exceptions-handling)
      (middlewares/wrap-jwt-guard auth-excluded)
      (middlewares/wrap-deps component)
      (prometheus-ring/wrap-metrics registry {:path "/lumin/metrics" :exception-status 500})
      (wrap-keyword-params)
      (wrap-params)
      (wrap-multipart-params)
      (wrap-json-params)
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :put :post :delete :patch])
      (middlewares/wrap-content-type-json)
      (middlewares/wrap-not-found)
      (middlewares/wrap-request-logging)))
