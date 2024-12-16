
(ns components.http-server.router
  (:require [compojure.core :refer [routes]]
            [modules.library.controller :as library]
            [modules.file.controller :as file]
            [modules.widget.controller :as widget]
            [modules.project.controller :as project]
            [modules.docker.controller :as docker]))

(def app-routes
  (apply routes (concat '() (library/routes) (project/routes) (widget/routes) (file/routes) (docker/routes))))