(ns modules.library.service
  (:require [modules.library.model :as library-model]
            [clojure.data.json :as json]))

(defn get-all-projects
  [ds authorized-id]
  (let [projects (library-model/get-all-user-projects ds authorized-id)]
    (json/write-str projects)))

(defn get-all-widgets
  [ds authorized-id]
  (-> (library-model/get-all-user-widgets ds authorized-id)
      (json/write-str)))

(defn get-all-categories
  [ds]
  (let [categories (library-model/get-all-categories ds)]
    (json/write-str categories)))