(ns plane-client.users
  "Plane API client for Users"
  (:require [plane-client.core :as core]
            [clojure.string :as str]))

;; ============================================================================
;; User Operations
;; ============================================================================

(defn list-workspace-members
  "List all members in a workspace.
  
  Parameters:
  - settings: Plane settings map
  - workspace: Workspace slug (optional)
  
  Returns: Vector of member maps"
  [settings & [{:keys [workspace]}]]
  (let [ws (or workspace (core/get-workspace-slug settings))
        path (str "/api/v1/workspaces/" ws "/workspace-members/")
        response (core/get-request settings path {:workspace ws})]
    (if (:success response)
      (:data response)
      (do
        (println "Failed to list workspace members:" (:error response))
        []))))

(defn find-user-by-email
  "Find a user by email address.
  
  Parameters:
  - settings: Plane settings map
  - email: Email address to find
  
  Returns: User member map or nil"
  [settings email]
  (when email
    (let [members (list-workspace-members settings)
          email-lower (str/lower-case (str/trim (str email)))]
      (first
       (filter
        (fn [member]
          (when-let [member-email (get-in member [:member :email])]
            (= email-lower (str/lower-case (str member-email)))))
        members)))))

(defn find-user-by-display-name
  "Find a user by display name (e.g., 'nico').
  
  Parameters:
  - settings: Plane settings map
  - display-name: Display name to find
  
  Returns: User member map or nil"
  [settings display-name]
  (when display-name
    (let [members (list-workspace-members settings)
          name-lower (str/lower-case (str/trim (str display-name)))]
      (first
       (filter
        (fn [member]
          (when-let [member-name (get-in member [:member :display_name])]
            (= name-lower (str/lower-case (str member-name)))))
        members)))))

(defn find-user
  "Find a user by email or display name.
  
  Parameters:
  - settings: Plane settings map
  - identifier: Email or display name
  
  Returns: User member ID or nil"
  [settings identifier]
  (when identifier
    (or
     (when-let [user (find-user-by-email settings identifier)]
       (get-in user [:member :id]))
     (when-let [user (find-user-by-display-name settings identifier)]
       (get-in user [:member :id])))))
