(ns components.http-server.router
  (:require [compojure.core :refer [routes]]
            [modules.library.controller :as library]
            [modules.project.controller :as project]))

(def app-routes
  (apply routes (concat '() (library/routes) (project/routes))))