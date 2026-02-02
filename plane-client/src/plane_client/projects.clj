(ns plane-client.projects
  "Plane API client for Projects"
  (:require [plane-client.core :as core]))

;; ============================================================================
;; Project Operations
;; ============================================================================

(defn list-projects
  "List all projects in a workspace.

  Parameters:
  - settings: Plane settings map
  - workspace: Workspace slug (optional, uses default from settings)

  Returns: Vector of project maps"
  [settings & [{:keys [workspace]}]]
  (let [ws (or workspace (core/get-workspace-slug settings))
        path (str "/api/v1/workspaces/" ws "/projects/")
        response (core/get-request settings path {:workspace ws})]
    (if (:success response)
      ;; Handle paginated response - extract :results from data map
      (let [data (:data response)]
        (if (map? data)
          (or (:results data) [data])
          data))
      (do
        (println "Failed to list projects:" (:error response))
        []))))

(defn get-project
  "Get details of a specific project.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - workspace: Workspace slug (optional)

  Returns: Project map or nil"
  [settings project-id & [{:keys [workspace]}]]
  (let [ws (or workspace (core/get-workspace-slug settings))
        path (str "/api/v1/workspaces/" ws "/projects/" project-id "/")
        response (core/get-request settings path {:workspace ws})]
    (when (:success response)
      (:data response))))

(defn create-project
  "Create a new project.

  Parameters:
  - settings: Plane settings map
  - project-data: Map with project details:
    - :name (required) - Project name
    - :identifier (required) - Short project identifier (e.g., 'PROJ')
    - :description - Project description
    - :network - Visibility (0=private, 2=public)
    - :project_lead - User ID of project lead
  - workspace: Workspace slug (optional)

  Returns: Created project map or nil"
  [settings project-data & [{:keys [workspace]}]]
  (let [ws (or workspace (core/get-workspace-slug settings))
        path (str "/api/v1/workspaces/" ws "/projects/")
        response (core/post-request settings path project-data {:workspace ws})]
    (if (:success response)
      (do
        (println "✓ Project created:" (:name project-data))
        (:data response))
      (do
        (println "✗ Failed to create project:" (:error response))
        nil))))

(defn update-project
  "Update an existing project.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - updates: Map with fields to update
  - workspace: Workspace slug (optional)

  Returns: Updated project map or nil"
  [settings project-id updates & [{:keys [workspace]}]]
  (let [ws (or workspace (core/get-workspace-slug settings))
        path (str "/api/v1/workspaces/" ws "/projects/" project-id "/")
        response (core/patch-request settings path updates {:workspace ws})]
    (if (:success response)
      (do
        (println "✓ Project updated")
        (:data response))
      (do
        (println "✗ Failed to update project:" (:error response))
        nil))))

(defn delete-project
  "Delete a project.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - workspace: Workspace slug (optional)

  Returns: true if successful, false otherwise"
  [settings project-id & [{:keys [workspace]}]]
  (let [ws (or workspace (core/get-workspace-slug settings))
        path (str "/api/v1/workspaces/" ws "/projects/" project-id "/")
        response (core/delete-request settings path {:workspace ws})]
    (if (:success response)
      (do
        (println "✓ Project deleted")
        true)
      (do
        (println "✗ Failed to delete project:" (:error response))
        false))))

;; ============================================================================
;; Display Helpers
;; ============================================================================

(defn print-project
  "Pretty-print a project"
  [project]
  (println "\n📁" (:name project))
  (println "   ID:" (:id project))
  (println "   Identifier:" (:identifier project))
  (when (:description project)
    (println "   Description:" (:description project)))
  (println "   Created:" (:created_at project)))

(defn print-projects
  "Pretty-print a list of projects"
  [projects]
  (println "\n=== Projects ===")
  (println "Total:" (count projects))
  (doseq [project projects]
    (print-project project)))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn -main [& args]
  (println "=== Plane Projects ===\n")

  (let [settings (core/load-settings)]
    (if-not settings
      (System/exit 1)
      (let [[command & rest-args] args]
        (case command
          "list"
          (let [projects (list-projects settings)]
            (print-projects projects))

          "get"
          (let [[project-id] rest-args]
            (if-not project-id
              (println "Usage: clojure -M:projects get <project-id>")
              (if-let [project (get-project settings project-id)]
                (print-project project)
                (println "Project not found"))))

          "create"
          (let [[name identifier description] rest-args]
            (if-not (and name identifier)
              (do
                (println "Usage: clojure -M:projects create <name> <identifier> [description]")
                (println "Example: clojure -M:projects create \"My Project\" \"MYPROJ\" \"A test project\""))
              (create-project settings
                              {:name name
                               :identifier identifier
                               :description description})))

          ;; Default: show usage
          (do
            (println "Usage: clojure -M:projects <command> [args]")
            (println)
            (println "Commands:")
            (println "  list                  - List all projects")
            (println "  get <id>             - Get project details")
            (println "  create <name> <id> [desc] - Create a new project")
            (println)
            (println "Examples:")
            (println "  clojure -M:projects list")
            (println "  clojure -M:projects get abc123")
            (println "  clojure -M:projects create \"My Project\" \"MYPROJ\" \"Description\"")
            (when-not command
              (System/exit 1))))))))

(comment
  ;; Examples - try these in the REPL

  ;; 1. Load settings
  (def settings (core/load-settings))

  ;; 2. List all projects
  (def projects (list-projects settings))
  (print-projects projects)

  ;; 3. Get a specific project
  (def project (get-project settings "project-id-here"))
  (print-project project)

  ;; 4. Create a new project
  (create-project settings
                  {:name "Test Project"
                   :identifier "TEST"
                   :description "A test project created via API"})

  ;; 5. Update a project
  (update-project settings
                  "project-id-here"
                  {:name "Updated Name"
                   :description "Updated description"})

  ;; 6. Delete a project
  (delete-project settings "project-id-here")

  ;; Run from CLI
  (-main "list"))
