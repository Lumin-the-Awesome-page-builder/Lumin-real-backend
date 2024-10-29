(ns components.http-server.router
  (:require [compojure.core :refer [routes]]))

(def app-routes
  (apply routes (concat '())))