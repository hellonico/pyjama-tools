(ns plane-client.users
  "Plane API client for Users"
  (:require [plane-client.core :as core]
            [clojure.string :as str]))

;; ============================================================================
;; User Operations
;; ============================================================================

(defn list-members
  "List members in a workspace or project.
  
  Tries multiple endpoints to support different Plane versions.
  Normalizes the result to a common structure."
  [settings & [{:keys [workspace project-id]}]]
  (let [ws (or workspace (core/get-workspace-slug settings))
        paths (cond-> [(str "/api/v1/workspaces/" ws "/workspace-members/")]
                project-id (conj (str "/api/v1/workspaces/" ws "/projects/" project-id "/members/")))
        ;; Try endpoints in reverse order (project-specific first if provided)
        paths (if project-id (reverse paths) paths)
        response (core/try-endpoints settings :get paths {:workspace ws})]
    (if (:success response)
      (let [data (:data response)
            results (if (map? data) (or (:results data) [data]) data)]
        ;; Normalize structure: some endpoints nest user in :member, others don't
        (mapv (fn [m]
                (if (:member m)
                  m ; already has :member key
                  {:member m})) ; wrap it to match expectations
              results))
      (do
        (println "Failed to list members")
        []))))

(defn find-user-by-email
  "Find a user by email address.
  
  Parameters:
  - settings: Plane settings map
  - email: Email address to find
  - opts: map with :project-id and :workspace
  
  Returns: User member map or nil"
  [settings email & [opts]]
  (when email
    (let [members (list-members settings opts)
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
  - opts: map with :project-id and :workspace
  
  Returns: User member map or nil"
  [settings display-name & [opts]]
  (when display-name
    (let [members (list-members settings opts)
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
  - opts: map with :project-id and :workspace
  
  Returns: User member ID or nil"
  [settings identifier & [opts]]
  (when identifier
    (or
     (when-let [user (find-user-by-email settings identifier opts)]
       (get-in user [:member :id]))
     (when-let [user (find-user-by-display-name settings identifier opts)]
       (get-in user [:member :id])))))
