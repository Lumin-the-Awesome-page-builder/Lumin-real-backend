(ns modules.widget.service
  (:refer-clojure :exclude [remove])
  (:require [clojure.data.json :as json]
            [modules.widget.model :refer [get-widget patch-widget create-widget set-tags get-tags remove-widget]]
            [utils.validator :as validator]))

(defn- has-access? [user-id widget]
  (when (not widget)
    (throw (ex-info "Not found" {:errors "Widget not found"})))
  (if (and widget (= (:owner_id widget) user-id))
    widget
    (throw (ex-info "Forbidden" {}))))

(defn get-by-id
  [ds authorized-id widget-id]
  (let [widget (->> widget-id
                    (get-widget ds)
                    (has-access? authorized-id))]
    (json/write-str (assoc widget :tags (map :tag (get-tags ds widget-id))))))

(def WidgetPatchSpec
  [:map
   [:name {:optional true} :string]
   [:data {:optional true} :string]
   [:category_id {:optional true} int?]
   [:tags {:optional true} [:sequential string?]]])

(defn patch
  [ds authorized-id widget-id patch-data]
  (let [validated (validator/validate WidgetPatchSpec patch-data)
        widget (->> widget-id
                    (get-widget ds)
                    (has-access? authorized-id))
        patch-result (-> (patch-widget ds widget-id (into widget validated))
                         (:next.jdbc/update-count)
                         (> 0))
        tags (map :tag (if (:tags validated)
                         (set-tags ds widget-id (:tags validated))
                         (get-tags ds widget-id)))]
    (json/write-str (if patch-result
                      (-> (get-widget ds widget-id)
                          (assoc :tags tags))
                      {:status 400}))))

(def WidgetCreateSpec
  [:map
   [:name :string]
   [:data :string]
   [:category_id {:optional true} int?]
   [:tags {:optional true} [:sequential string?]]])

(defn create
  [ds authorized-id widget-data]
  (let [validated (validator/validate WidgetCreateSpec widget-data)
        created (create-widget ds (assoc validated :owner_id authorized-id))
        tags (if (:tags validated)
               (map :tag (set-tags ds (:id created) (:tags validated)))
               [])]
    (json/write-str (assoc created :tags tags))))

(defn remove
  [ds authorized-id widget-id]
  (->> widget-id
       (get-widget ds)
       (has-access? authorized-id))
  (let [remove-result (-> (remove-widget ds widget-id)
                          (:next.jdbc/update-count)
                          (> 0))]
    (json/write-str (if remove-result
                      {:status 200}
                      {:status 400}))))
