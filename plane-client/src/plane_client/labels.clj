(ns plane-client.labels
  "Plane API client for Labels"
  (:require [plane-client.core :as core]
            [clojure.string :as str]))

;; ============================================================================
;; Label Operations
;; ============================================================================

(defn list-labels
  "List all labels in a project.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - workspace: Workspace slug (optional)
  
  Returns: Vector of label maps"
  [settings project-id & [{:keys [workspace]}]]
  (let [ws (or workspace (core/get-workspace-slug settings))
        path (str "/api/v1/workspaces/" ws "/projects/" project-id "/labels/")
        response (core/get-request settings path {:workspace ws})]
    (if (:success response)
      (let [data (:data response)]
        (if (map? data)
          (or (:results data) [data])
          data))
      (do
        (println "Failed to list labels:" (:error response))
        []))))

(defn find-label-by-name
  "Find a label by name (case-insensitive).
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - label-name: Label name to find
  
  Returns: Label map or nil"
  [settings project-id label-name]
  (when label-name
    (let [labels (list-labels settings project-id)
          label-name-lower (str/lower-case (str label-name))]
      (first
       (filter
        (fn [label]
          (= label-name-lower
             (str/lower-case (str (:name label)))))
        labels)))))

(defn create-label
  "Create a new label in a project.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - label-data: Map with :name (required) and optional :color
  - workspace: Workspace slug (optional)
  
  Returns: Created label map or nil"
  [settings project-id label-data & [{:keys [workspace]}]]
  (let [ws (or workspace (core/get-workspace-slug settings))
        path (str "/api/v1/workspaces/" ws "/projects/" project-id "/labels/")
        ;; Add default color if not provided
        data (merge {:color "#3b82f6"} label-data)
        response (core/post-request settings path data {:workspace ws})]
    (if (:success response)
      (do
        (println "✓ Label created:" (:name label-data))
        (:data response))
      (do
        (println "✗ Failed to create label:" (:error response))
        nil))))

(defn get-or-create-label
  "Get existing label or create it if it doesn't exist.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - label-name: Label name
  
  Returns: Label map (existing or newly created)"
  [settings project-id label-name]
  (or (find-label-by-name settings project-id label-name)
      (create-label settings project-id {:name label-name})))

(defn get-or-create-labels
  "Get or create multiple labels.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - label-names: Vector of label names
  
  Returns: Vector of label IDs"
  [settings project-id label-names]
  (when (seq label-names)
    (->> label-names
         (map #(get-or-create-label settings project-id %))
         (filter some?)
         (map :id)
         vec)))
