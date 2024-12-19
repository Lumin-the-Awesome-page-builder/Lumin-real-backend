(ns exposed-bash-service
  (:gen-class)
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [components.ring-server.middleware :as middlewares]
            [ring.adapter.jetty9 :refer [run-jetty]]
            [ring.util.response :refer [response]]
            [compojure.core :refer [routes POST]]
            [clojure.java.shell :refer [sh]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.middleware.cors :refer [wrap-cors]]))

(defn run-bash [command dir]
  (try
    (let [res (apply sh (into command [:dir dir]))]
      (log/info res)
      res)
    (catch Exception _
      (log/info _)
      {:exit 1 :out "" :err "command failed"})))


(defn handler []
  (POST "/" request
    (let [{:keys [command dir]} (:params request)]
      (log/info request)
      (response (json/write-str (run-bash command dir))))))

(defn -main []
  (run-jetty (-> (routes (handler))
                 (wrap-keyword-params)
                 (wrap-params)
                 (wrap-json-params)
                 (wrap-cors :access-control-allow-origin [#".*"]
                            :access-control-allow-methods [:get :put :post :delete :patch])
                 (middlewares/wrap-content-type-json))
             {:port 9090
              :join? false}))

(-main)