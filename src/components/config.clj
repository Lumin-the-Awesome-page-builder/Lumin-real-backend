(ns components.config
  (:require [aero.core :refer [read-config]]))

(defn fetch-config []
  #_:clj-kondo/ignore
  (read-config (clojure.java.io/resource "config.edn")
               {:resolver {"jwt.edn" (clojure.java.io/resource "jwt.edn")
                           "secrets.edn" (clojure.java.io/resource "secrets.edn")
                           "file_path.edn" (clojure.java.io/resource "file_path.edn")}}))