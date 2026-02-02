(ns gitlab-client.merge-requests
  "GitLab Merge Requests API"
  (:require [gitlab-client.core :as core]
            [clojure.string :as str]))

;; ============================================================================
;; Merge Requests Operations
;; ============================================================================

(defn list-assigned-merge-requests
  "List merge requests assigned to the current user.
  
  Parameters:
  - settings: GitLab settings map
  - opts: Optional map with:
    - :state - Filter by state: opened, closed, locked, merged, all (default: opened)
    - :scope - Filter by scope: created_by_me, assigned_to_me, all (default: assigned_to_me)
    - :per-page - Items per page (default: 20, max: 100)
    - :page - Page number
  
  Returns: Vector of merge request maps"
  [settings & [opts]]
  (let [query-params (cond-> {:scope (or (:scope opts) "assigned_to_me")
                              :state (or (:state opts) "opened")}
                       (:per-page opts) (assoc :per_page (:per-page opts))
                       (:page opts) (assoc :page (:page opts)))
        response (core/request settings :get "/merge_requests" {:query-params query-params})]
    
    (if (:success response)
      (:data response)
      (do
        (println "Failed to list merge requests:" (:error response))
        []))))

(defn get-merge-request
  "Get details of a specific merge request.
  
  Parameters:
  - settings: GitLab settings map
  - project-id: Project ID or path (e.g., \"group/project\" or 123)
  - mr-iid: Merge request IID (internal ID within project)
  
  Returns: Merge request map or nil"
  [settings project-id mr-iid]
  (let [project-path (if (string? project-id)
                       (str/replace project-id "/" "%2F")
                       project-id)
        path (str "/projects/" project-path "/merge_requests/" mr-iid)
        response (core/request settings :get path {})]
    
    (when (:success response)
      (:data response))))

(defn get-merge-request-changes
  "Get changes (diff) for a merge request.
  
  Parameters:
  - settings: GitLab settings map
  - project-id: Project ID or path
  - mr-iid: Merge request IID
  
  Returns: Map with :changes (vector of file changes) and other MR details"
  [settings project-id mr-iid]
  (let [project-path (if (string? project-id)
                       (str/replace project-id "/" "%2F")
                       project-id)
        path (str "/projects/" project-path "/merge_requests/" mr-iid "/changes")
        response (core/request settings :get path {})]
    
    (when (:success response)
      (:data response))))

(defn get-merge-request-diffs
  "Get detailed diffs for a merge request.
  
  Parameters:
  - settings: GitLab settings map
  - project-id: Project ID or path
  - mr-iid: Merge request IID
  
  Returns: Vector of diff maps with detailed line-by-line changes"
  [settings project-id mr-iid]
  (let [project-path (if (string? project-id)
                       (str/replace project-id "/" "%2F")
                       project-id)
        path (str "/projects/" project-path "/merge_requests/" mr-iid "/diffs")
        response (core/request settings :get path {})]
    
    (if (:success response)
      (:data response)
      [])))

(defn add-merge-request-note
  "Add a comment/note to a merge request.
  
  Parameters:
  - settings: GitLab settings map
  - project-id: Project ID or path
  - mr-iid: Merge request IID
  - body: Comment text (supports markdown)
  
  Returns: Created note map or nil"
  [settings project-id mr-iid body]
  (let [project-path (if (string? project-id)
                       (str/replace project-id "/" "%2F")
                       project-id)
        path (str "/projects/" project-path "/merge_requests/" mr-iid "/notes")
        response (core/request settings :post path {:body {:body body}})]
    
    (if (:success response)
      (do
        (println "✓ Comment added to MR")
        (:data response))
      (do
        (println "✗ Failed to add comment:" (:error response))
        nil))))

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn extract-diff-content
  "Extract unified diff content from changes.
  
  Parameters:
  - changes: Changes map from get-merge-request-changes
  
  Returns: String with unified diff format"
  [changes]
  (when-let [files (:changes changes)]
    (->> files
         (map (fn [file]
                (str "diff --git a/" (:old_path file) " b/" (:new_path file) "\n"
                     (:diff file))))
         (str/join "\n\n"))))

(defn print-merge-request
  "Pretty-print a merge request"
  [mr]
  (println "\n🔀" (:title mr))
  (println "   Project:" (:project_id mr))
  (println "   IID:" (:iid mr))
  (println "   State:" (:state mr))
  (println "   Author:" (get-in mr [:author :name]))
  (when (:assignees mr)
    (println "   Assignees:" (str/join ", " (map :name (:assignees mr)))))
  (println "   Source:" (:source_branch mr) "→" (:target_branch mr))
  (println "   URL:" (:web_url mr)))

(defn print-merge-requests
  "Pretty-print a list of merge requests"
  [mrs]
  (println "\n=== Merge Requests ===\")
  (println "Total:" (count mrs))
  (doseq [mr mrs]
    (print-merge-request mr)))

(comment
  ;; Example usage
  (require '[gitlab-client.core :as gitlab])
  (def settings (gitlab/load-settings))
  
  ;; List assigned MRs
  (def mrs (list-assigned-merge-requests settings))
  (print-merge-requests mrs)
  
  ;; Get specific MR
  (def mr (get-merge-request settings "group/project" 123))
  (print-merge-request mr)
  
  ;; Get MR changes/diff
  (def changes (get-merge-request-changes settings "group/project" 123))
  (def diff-content (extract-diff-content changes))
  (println diff-content)
  
  ;; Add comment
  (add-merge-request-note settings "group/project" 123 "LGTM! 🚀"))
