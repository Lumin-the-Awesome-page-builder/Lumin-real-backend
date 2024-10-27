(ns components.config
  (:require [aero.core :refer [read-config]]))

(defn fetch-config []
  #_:clj-kondo/ignore
  (read-config (clojure.java.io/resource "config.edn")))