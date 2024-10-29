(ns components.http-server.router
  (:require [compojure.core :refer [routes]]
            [modules.library.controller :as library]))

(def app-routes
  (apply routes (concat '() library/routes)))