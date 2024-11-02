(ns components.config
  (:require [aero.core :refer [read-config]]))

#_:clj-kondo/ignore
(println (.getPath (clojure.java.io/resource "jwt.edn")))

(defn fetch-config []
  #_:clj-kondo/ignore
  (read-config (clojure.java.io/resource "config.edn")
               {:resolver {"jwt.edn" (clojure.java.io/resource "jwt.edn")
                           "secrets.edn" (clojure.java.io/resource "secrets.edn")}}))