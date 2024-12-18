(ns components.config
  (:require [aero.core :refer [read-config]]))

(defn fetch-config []
  #_:clj-kondo/ignore
  (read-config (clojure.java.io/resource "config.edn")
               {:resolver {"jwt.edn" (clojure.java.io/resource "jwt.edn")
                           "jws.edn" (clojure.java.io/resource "jws.edn")
                           "secrets.edn" (clojure.java.io/resource "secrets.edn")
                           "file_path.edn" (clojure.java.io/resource "file_path.edn")
                           "docker_path.edn" (clojure.java.io/resource "docker_path.edn")
                           "docker_host_path.edn" (clojure.java.io/resource "docker_host_path.edn")}}))