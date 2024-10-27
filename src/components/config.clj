(ns components.config
  (:require [aero.core :refer [read-config]]))

(defn fetch-config []
  (read-config (clojure.java.io/resource "config.edn")))