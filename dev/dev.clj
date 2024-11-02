(ns dev
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [core :refer [create-system]]))

(def system (atom (component/start-system (create-system))))
(log/info "System started")
(defn reload-system []
  (reset! system (-> @system
                     (component/stop-system)
                     (component/start-system))))

;(def debug-source (component/start (datasource-component)))

;(reload-system)
