(ns modules.widget.service
  (:refer-clojure :exclude [remove])
  (:require [clojure.data.json :as json]
            [modules.widget.model :refer [get-widget patch-widget patch-widget-preview create-widget set-tags get-tags remove-widget]]
            [utils.validator :as validator]
            [utils.file :as f]))

(defn- has-access? [user-id widget]
  (when (not widget)
    (throw (ex-info "Not found" {:errors "Widget not found"})))
  (if (and widget (= (:owner_id widget) user-id))
    widget
    (throw (ex-info "Not found" {}))))

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

(defn patch-preview
  [ds authorized-id widget-id preview]
  (let [widget (->> widget-id
                    (get-widget ds)
                    (has-access? authorized-id))
        preview-file-name (str "Widget" (System/currentTimeMillis) (:id widget) ".png")]
    (when (:preview widget)
      (f/drop-file (:preview widget)))
    (patch-widget-preview ds widget-id preview-file-name)
    (f/save-base64-file preview preview-file-name)
    preview-file-name))

(def WidgetCreateSpec
  [:map
   [:name :string]
   [:data :string]
   [:category_id {:optional true} int?]
   [:preview :string]
   [:tags {:optional true} [:sequential string?]]])

(defn create
  [ds authorized-id widget-data]
  (let [validated (validator/validate WidgetCreateSpec widget-data)
        created (create-widget ds (assoc validated :owner_id authorized-id))
        tags (if (:tags validated)
               (map :tag (set-tags ds (:id created) (:tags validated)))
               [])
        preview (if (:preview validated)
                  (patch-preview ds authorized-id (:id created) (:preview validated))
                  nil)]
    (json/write-str (-> created
                        (assoc :tags tags)
                        (assoc :preview preview)))))

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
