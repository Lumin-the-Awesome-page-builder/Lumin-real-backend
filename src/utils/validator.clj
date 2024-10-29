(ns utils.validator
  (:require [malli.core :as m]
            [malli.error :as me]))

(defn validate [spec data]
  (let [validated (m/validate spec data)]
    (when (not validated)
      (throw (ex-info "Bad request" {:errors (-> (m/explain spec data)
                                                 (me/humanize))})))
    data))
