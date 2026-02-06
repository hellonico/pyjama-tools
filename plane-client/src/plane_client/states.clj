(ns plane-client.states
  "Plane API client for States"
  (:require [plane-client.core :as core]))

;; ============================================================================
;; State Operations
;; ============================================================================

(defn list-states
  "List all states in a project.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - workspace: Workspace slug (optional)
  
  Returns: Vector of state maps"
  [settings project-id & [{:keys [workspace]}]]
  (let [ws (or workspace (core/get-workspace-slug settings))
        path (str "/api/v1/workspaces/" ws "/projects/" project-id "/states/")
        response (core/get-request settings path {:workspace ws})]
    (if (:success response)
      (:data response)
      (do
        (println "Failed to list states:" (:error response))
        []))))

(defn find-state-by-name
  "Find a state by name (case-insensitive).
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - state-name: State name to find (e.g., 'in-progress', 'done')
  
  Returns: State map or nil"
  [settings project-id state-name]
  (when state-name
    (let [states (list-states settings project-id)
          state-name-lower (clojure.string/lower-case (str state-name))]
      (first
       (filter
        (fn [state]
          (= state-name-lower
             (clojure.string/lower-case (str (:name state)))))
        states)))))
