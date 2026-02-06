(ns plane-client.work-items
       "Plane API client for Work Items (Issues)"
       (:require [plane-client.core :as core]
                 [clojure.java.io :as io]
                 [clj-http.client :as http]
                 [clojure.string]))

     ;; ============================================================================
     ;; Endpoint Configuration
     ;; ============================================================================

     (def ^:private endpoint-paths
       "Plane API endpoint paths - tries new endpoints first, falls back to legacy"
       {:list ["work-items" "issues"]          ; New first, then legacy
        :get ["work-items" "issues"]
        :create ["work-items" "issues"]
        :update ["work-items" "issues"]
        :delete ["work-items" "issues"]
        :search ["search-work-items" "search-issues"]})

     (defn- build-endpoint
       "Build endpoint path for work items operations.
  
  Supports both new (/work-items/) and legacy (/issues/) endpoints.
  Returns a vector of paths to try in order."
       [workspace project-id operation & [item-id]]
       (let [base (str "/api/v1/workspaces/" workspace "/projects/" project-id "/")
             paths (get endpoint-paths operation ["issues"])]
         (if item-id
           ;; Individual item operations
           (mapv #(str base % "/" item-id "/") paths)
           ;; List/create operations
           (mapv #(str base % "/") paths))))

;; ============================================================================

(defn list-work-items
  "List work items in a project.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - opts: Options map:
    - :workspace - Workspace slug (optional)
    - :per-page - Items per page for pagination
    - :state - Filter by state
    - :assignee - Filter by assignee

  Returns: Vector of work item maps
  
  Note: Automatically tries both new (/work-items/) and legacy (/issues/) endpoints."
  [settings project-id & [opts]]
  (let [ws (or (:workspace opts) (core/get-workspace-slug settings))
        paths (build-endpoint ws project-id :list)
        query-params (cond-> {}
                       (:state opts) (assoc :state (:state opts))
                       (:assignee opts) (assoc :assignees (:assignee opts)))
        response (core/try-endpoints settings :get paths
                                (-> opts
                                    (assoc :workspace ws)
                                    (assoc :query-params query-params)))]
    (if (:success response)
      (let [data (:data response)]
        ;; Handle both paginated and non-paginated responses
        (if (map? data)
          (or (:results data) [data])
          data))
      (do
        (println "Failed to list work items:" (:error response))
        []))))

(defn get-work-item
  "Get details of a specific work item.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - opts: Options map with :workspace

  Returns: Work item map or nil
  
  Note: Automatically tries both new (/work-items/) and legacy (/issues/) endpoints."
  [settings project-id work-item-id & [opts]]
  (let [ws (or (:workspace opts) (core/get-workspace-slug settings))
        paths (build-endpoint ws project-id :get work-item-id)
        response (core/try-endpoints settings :get paths {:workspace ws})]
    (when (:success response)
      (:data response))))

(defn create-work-item
  "Create a new work item.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-data: Map with work item details:
    - :name (required) - Work item title
    - :description - Work item description
    - :state - State ID
    - :priority - Priority (none, low, medium, high, urgent)
    - :assignees - Vector of user IDs
    - :labels - Vector of label IDs
  - opts: Options map with :workspace

  Returns: Created work item map or nil
  
  Note: Automatically tries both new (/work-items/) and legacy (/issues/) endpoints."
  [settings project-id work-item-data & [opts]]
  (let [ws (or (:workspace opts) (core/get-workspace-slug settings))
        paths (build-endpoint ws project-id :create)
        _ (println "\n🔍 DEBUG: Creating work item in Plane:")
        _ (println "   Name:" (:name work-item-data))
        _ (println "   Description length:" (count (str (:description work-item-data))))
        _ (println "   Description preview:" (subs (str (:description work-item-data)) 0 (min 150 (count (str (:description work-item-data))))))
        _ (println "   Priority:" (:priority work-item-data))
        response (core/try-endpoints settings :post paths
                                (assoc opts :body work-item-data :workspace ws))]
    (if (:success response)
      (do
        (println "✓ Work item created:" (:name work-item-data))
        (:data response))
      (do
        (println "✗ Failed to create work item:" (:error response))
        nil))))

(defn update-work-item
  "Update an existing work item.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - updates: Map with fields to update
  - opts: Options map with :workspace

  Returns: Updated work item map or nil
  
  Note: Automatically tries both new (/work-items/) and legacy (/issues/) endpoints."
  [settings project-id work-item-id updates & [opts]]
  (let [ws (or (:workspace opts) (core/get-workspace-slug settings))
        paths (build-endpoint ws project-id :update work-item-id)
        response (core/try-endpoints settings :patch paths
                                (assoc opts :body updates :workspace ws))]
    (if (:success response)
      (do
        (println "✓ Work item updated")
        (:data response))
      (do
        (println "✗ Failed to update work item:" (:error response))
        nil))))

(defn delete-work-item
  "Delete a work item.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - opts: Options map with :workspace

  Returns: true if successful, false otherwise
  
  Note: Automatically tries both new (/work-items/) and legacy (/issues/) endpoints."
  [settings project-id work-item-id & [opts]]
  (let [ws (or (:workspace opts) (core/get-workspace-slug settings))
        paths (build-endpoint ws project-id :delete work-item-id)
        response (core/try-endpoints settings :delete paths {:workspace ws})]
    (if (:success response)
      (do
        (println "✓ Work item deleted")
        true)
      (do
        (println "✗ Failed to delete work item:" (:error response))
        false))))

(defn search-work-items
  "Search work items in a project.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - query: Search query string
  - opts: Options map with :workspace

  Returns: Vector of matching work items
  
  Note: Automatically tries both new (/search-work-items/) and legacy (/search-issues/) endpoints."
  [settings project-id query & [opts]]
  (let [ws (or (:workspace opts) (core/get-workspace-slug settings))
        paths (build-endpoint ws project-id :search)
        response (core/try-endpoints settings :get paths
                                {:workspace ws
                                 :query-params {:search query}})]
    (if (:success response)
      (let [data (:data response)]
        ;; Handle both paginated and non-paginated responses
        (if (map? data)
          (or (:results data) [data])
          data))
      (do
        (println "Failed to search work items:" (:error response))
        []))))

;; ============================================================================
;; Attachments Operations
;; ============================================================================

(defn- multipart-upload
  "Upload a file using multipart form data.
  
  This is used for uploading to the storage provider (e.g., S3).
  Returns the response from the upload."
  [upload-url file-path]
  (try
    (let [file (io/file file-path)
          response (http/post upload-url
                              {:multipart [{:name "file"
                                            :content file}]
                               :throw-exceptions false})]
      {:success (<= 200 (:status response) 299)
       :status (:status response)
       :data response})
    (catch Exception e
      (println "✗ File upload failed:" (.getMessage e))
      {:success false
       :error :upload-failed
       :message (.getMessage e)})))

(defn upload-attachment
  "Upload a file as an attachment to a work item.
  
  This implements the Plane API 3-step attachment workflow:
  1. Get upload credentials (generates asset_id)
  2. Upload file to storage
  3. Complete the upload notification
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - file-path: Path to the file to upload
  - opts: Options map with:
    - :workspace - Workspace slug (optional)
    - :filename - Override filename (optional, defaults to file basename)
  
  Returns: Attachment map or nil on failure"
  [settings project-id work-item-id file-path & [opts]]
  (let [ws (or (:workspace opts) (core/get-workspace-slug settings))
        file (io/file file-path)
        filename (or (:filename opts) (.getName file))
        file-size (.length file)]

    (println "📎 Uploading attachment:" filename "(" file-size "bytes)")

    ;; Step 1: Get upload credentials
    (let [base (str "/api/v1/workspaces/" ws "/projects/" project-id "/")
          creds-paths [(str base "work-items/" work-item-id "/attachments/")
                       (str base "issues/" work-item-id "/attachments/")]
          creds-body {:name filename
                      :size file-size}
          creds-response (core/try-endpoints settings :post creds-paths
                                        {:workspace ws
                                         :body creds-body})]

      (if-not (:success creds-response)
        (do
          (println "✗ Failed to get upload credentials")
          nil)

        (let [creds-data (:data creds-response)
              asset-id (:asset_id creds-data)
              upload-url (:upload_url creds-data)]

          (println "  ✓ Got credentials, asset_id:" asset-id)

          ;; Step 2: Upload file to storage
          (println "  ⬆  Uploading to storage...")
          (let [upload-response (multipart-upload upload-url file-path)]

            (if-not (:success upload-response)
              (do
                (println "✗ File upload failed")
                nil)

              (do
                (println "  ✓ File uploaded to storage")

                ;; Step 3: Complete the upload (notify Plane)
                (let [complete-paths [(str base "work-items/" work-item-id "/attachments/" asset-id "/")
                                      (str base "issues/" work-item-id "/attachments/" asset-id "/")]
                      complete-body {:asset_id asset-id}
                      complete-response (core/try-endpoints settings :patch complete-paths
                                                       {:workspace ws
                                                        :body complete-body})]

                  (if (:success complete-response)
                    (do
                      (println "✓ Attachment uploaded successfully!")
                      (:data complete-response))
                    (do
                      (println "✗ Failed to complete upload")
                      nil)))))))))))

(defn list-attachments
  "List all attachments for a work item.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - opts: Options map with :workspace
  
  Returns: Vector of attachment maps"
  [settings project-id work-item-id & [opts]]
  (let [ws (or (:workspace opts) (core/get-workspace-slug settings))
        base (str "/api/v1/workspaces/" ws "/projects/" project-id "/")
        ;; Try multiple endpoint patterns for different Plane versions
        paths [(str base "work-items/" work-item-id "/attachments/")
               (str base "issues/" work-item-id "/attachments/")
               (str "/api/workspaces/" ws "/file-assets/?issue=" work-item-id)
               (str "/api/assets/v2/workspaces/" ws "/issues/" work-item-id "/")]
        response (core/try-endpoints settings :get paths {:workspace ws})]

    (if (:success response)
      (let [data (:data response)]
        (if (map? data)
          (or (:results data) [data])
          data))
      (do
        (println "Failed to list attachments:" (:error response))
        []))))

(defn download-attachment
  "Download an attachment from a work item to a local file.
  
  Parameters:
  - settings: Plane settings map
  - attachment: Attachment map (from list-attachments)
  - output-path: Path where the file should be saved
  
  Returns: true if successful, false otherwise"
  [settings attachment output-path]
  (let [asset-url (:asset attachment)
        filename (:attributes (:asset attachment) "attachment")]

    (if-not asset-url
      (do
        (println "✗ No asset URL found in attachment")
        false)

      (try
        (println "📥 Downloading:" filename "→" output-path)

        ;; Download the file from the asset URL
        (let [response (http/get asset-url {:as :byte-array
                                            :throw-exceptions false})]
          (if (<= 200 (:status response) 299)
            (do
              ;; Write the file
              (io/copy (:body response) (io/file output-path))
              (println "✓ Downloaded successfully!")
              true)
            (do
              (println "✗ Download failed with status:" (:status response))
              false)))

        (catch Exception e
          (println "✗ Download error:" (.getMessage e))
          false)))))

(defn download-all-attachments
  "Download all attachments for a work item to a directory.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - output-dir: Directory where files should be saved
  - opts: Options map with :workspace
  
  Returns: Vector of successfully downloaded file paths"
  [settings project-id work-item-id output-dir & [opts]]
  (let [attachments (list-attachments settings project-id work-item-id opts)]

    (if (empty? attachments)
      (do
        (println "No attachments found")
        [])

      (do
        (println "\n📦 Found" (count attachments) "attachment(s)")

        ;; Create output directory if it doesn't exist
        (.mkdirs (io/file output-dir))

        (doall
         (for [att attachments
               :let [filename (or (:attributes (:asset att))
                                  (str "attachment-" (:id att)))
                     output-path (str output-dir "/" filename)]]
           (do
             (println "\n" (inc (.indexOf attachments att)) "of" (count attachments))
             (when (download-attachment settings att output-path)
               output-path))))))))

;; ============================================================================
;; Comments Operations
;; ============================================================================

(defn add-comment
  "Add a comment to a work item.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - comment: Comment text (will be converted to HTML)
  - opts: Options map with :workspace

  Returns: Created comment map or nil
  
  Note: Automatically tries both new and legacy endpoints."
  [settings project-id work-item-id comment & [opts]]
  (let [ws (or (:workspace opts) (core/get-workspace-slug settings))
        ;; Build comment paths - note: uses 'issues' for legacy, 'work-items' for new
        base (str "/api/v1/workspaces/" ws "/projects/" project-id "/")
        paths [(str base "work-items/" work-item-id "/comments/")
               (str base "issues/" work-item-id "/comments/")]
        comment-html (str "<p>" comment "</p>")
        response (core/try-endpoints settings :post paths
                                (assoc opts
                                       :body {:comment_html comment-html}
                                       :workspace ws))]
    (if (:success response)
      (do
        (println "✓ Comment added")
        (:data response))
      (do
        (println "✗ Failed to add comment:" (:error response))
        nil))))

(defn list-comments
  "List all comments for a work item.

  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - opts: Options map with :workspace

  Returns: Vector of comment maps
  
  Note: Automatically tries both new and legacy endpoints."
  [settings project-id work-item-id & [opts]]
  (let [ws (or (:workspace opts) (core/get-workspace-slug settings))
        base (str "/api/v1/workspaces/" ws "/projects/" project-id "/")
        paths [(str base "work-items/" work-item-id "/comments/")
               (str base "issues/" work-item-id "/comments/")]
        response (core/try-endpoints settings :get paths {:workspace ws})]
    (if (:success response)
      (let [data (:data response)]
        (if (map? data)
          (or (:results data) [data])
          data))
      (do
        (println "Failed to list comments:" (:error response))
        []))))


;; ============================================================================
;; Display Helpers
;; ============================================================================

(defn priority-emoji
  "Get emoji for priority level"
  [priority]
  (case priority
    "urgent" "🔴"
    "high" "🟠"
    "medium" "🟡"
    "low" "🟢"
    "none" "⚪"
    "❓"))

(defn print-work-item
  "Pretty-print a work item"
  [work-item]
  (println "\n" (priority-emoji (:priority work-item)) (:name work-item))
  (println "   ID:" (:id work-item))
  (when (:sequence_id work-item)
    (println "   Sequence:" (:sequence_id work-item)))
  (when (:state_detail work-item)
    (println "   State:" (:name (:state_detail work-item))))
  (when (:priority work-item)
    (println "   Priority:" (:priority work-item)))
  (when (:description work-item)
    (println "   Description:" (subs (:description work-item) 0 (min 100 (count (:description work-item)))) "..."))
  (println "   Created:" (:created_at work-item)))

(defn print-work-items
  "Pretty-print a list of work items"
  [work-items]
  (println "\n=== Work Items ===")
  (println "Total:" (count work-items))
  (doseq [item work-items]
    (print-work-item item)))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn -main [& args]
  (println "=== Plane Work Items ===\n")

  (let [settings (core/load-settings)]
    (if-not settings
      (System/exit 1)
      (let [[command project-id & rest-args] args]
        (cond
          (not project-id)
          (do
            (println "Usage: clojure -M:work-items <command> <project-id> [args]")
            (println)
            (println "Commands:")
            (println "  list <project-id>              - List work items")
            (println "  get <project-id> <item-id>     - Get work item details")
            (println "  create <project-id> <name>     - Create work item")
            (println "  search <project-id> <query>    - Search work items")
            (println)
            (println "Examples:")
            (println "  clojure -M:work-items list my-project-id")
            (println "  clojure -M:work-items get my-project-id work-item-123")
            (println "  clojure -M:work-items create my-project-id \"Fix bug in login\"")
            (println "  clojure -M:work-items search my-project-id \"authentication\"")
            (System/exit 1))

          :else
          (case command
            "list"
            (let [items (list-work-items settings project-id)]
              (print-work-items items))

            "get"
            (let [[item-id] rest-args]
              (if-not item-id
                (println "Usage: clojure -M:work-items get <project-id> <item-id>")
                (if-let [item (get-work-item settings project-id item-id)]
                  (print-work-item item)
                  (println "Work item not found"))))

            "create"
            (let [[name & desc-parts] rest-args
                  description (when desc-parts (clojure.string/join " " desc-parts))]
              (if-not name
                (println "Usage: clojure -M:work-items create <project-id> <name> [description]")
                (create-work-item settings
                                  project-id
                                  {:name name
                                   :description description})))

            "search"
            (let [query (clojure.string/join " " rest-args)]
              (if (empty? query)
                (println "Usage: clojure -M:work-items search <project-id> <query>")
                (let [items (search-work-items settings project-id query)]
                  (print-work-items items))))

            ;; Unknown command
            (do
              (println "Unknown command:" command)
              (println "Use -M:work-items without args to see usage")
              (System/exit 1))))))))

(comment
  ;; Examples - try these in the REPL

  ;; 1. Load settings
  (def settings (core/load-settings))

  ;; 2. List work items in a project
  (def items (list-work-items settings "project-id-here"))
  (print-work-items items)

  ;; 3. Get a specific work item
  (def item (get-work-item settings "project-id" "work-item-id"))
  (print-work-item item)

  ;; 4. Create a work item
  (create-work-item settings
                    "project-id"
                    {:name "Fix authentication bug"
                     :description "Users unable to login with Google OAuth"
                     :priority "high"})

  ;; 5. Update a work item
  (update-work-item settings
                    "project-id"
                    "work-item-id"
                    {:priority "urgent"
                     :state "in-progress-state-id"})

  ;; 6. Search work items
  (def results (search-work-items settings "project-id" "authentication"))
  (print-work-items results)

  ;; 7. Delete a work item
  (delete-work-item settings "project-id" "work-item-id")

  ;; Run from CLI
  (-main "list" "my-project-id"))
