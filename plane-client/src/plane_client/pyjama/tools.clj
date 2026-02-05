(ns plane-client.pyjama.tools
  "Pyjama tools for Plane API operations - for use with jetlag and other agents"
  (:require [plane-client.core :as plane]
            [plane-client.projects :as projects]
            [plane-client.work-items :as items]
            [plane-client.attachments :as att]
            [plane-client.email-utils :as email-utils]
            [plane-client.pyjama.email-confirmation :as email-confirm]
            [clojure.string :as str]))

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn extract-issue-key
  "Extract issue key from email subject.
  
  Looks for patterns like:
  - [EDEMO-123] in brackets
  - EDEMO-123 without brackets
  - Re: [EDEMO-123] or Re: EDEMO-123
  
  Returns the issue key (e.g., 'EDEMO-123') or nil"
  [subject]
  (when subject
    (let [result (or
                  ;; Try to find [EDEMO-123] pattern in brackets
                  (when-let [match (re-find #"\[([A-Z]+-\d+)\]" subject)]
                    (second match))
                  ;; Try to find EDEMO-123 pattern without brackets
                  (when-let [match (re-find #"([A-Z]+-\d+)" subject)]
                    (second match)))]
      (println "🔍 Extracting issue key from subject:" subject)
      (println "   → Found issue key:" (or result "NONE"))
      result)))

(defn email-subject-to-issue-title
  "Convert email subject to issue title"
  [subject]
  (-> subject
      (str/replace #"(?i)^RE:\s*" "")  ; Case-insensitive "Re:" removal
      (str/replace #"(?i)^FWD:\s*" "")  ; Also handle forwards
      str/trim))

(defn find-existing-issue
  "Find an existing work item by matching issue key in subject.
  
  Searches for the issue key in:
  1. The work item's name field
  2. The work item's sequence_id (e.g., EDEMO-123)
  
  If no issue key is found, falls back to matching by title.
  
  Returns the matching work item or nil"
  [settings project-id subject]
  (let [issue-key (extract-issue-key subject)]
    (if issue-key
      ;; If we have an issue key, search by key
      (let [all-items (items/list-work-items settings project-id)
            _ (println "   → Searching through" (count all-items) "work items for key:" issue-key)
            _ (println "   → Checking each item:")
            matched-item (first (filter (fn [item]
                                          (let [item-name (str (:name item))
                                                item-seq-id (:sequence_id item)
                                                item-proj-id (:project_identifier item)
                                                constructed-key (when item-seq-id
                                                                  (str item-proj-id "-" item-seq-id))
                                                name-match? (str/includes? item-name issue-key)
                                                seq-match? (= issue-key constructed-key)
                                                matches? (or name-match? seq-match?)]
                                            (println (str "      - " item-name
                                                          " (seq: " item-seq-id
                                                          ", proj: " item-proj-id
                                                          ", key: " constructed-key ")"
                                                          (if matches? " ✓ MATCH" "")))
                                            (when matches?
                                              (println "   ✓ MATCH FOUND by key:" item-name "(" issue-key ")"))
                                            matches?))
                                        all-items))]
        (when-not matched-item
          (println "   → No matching issue found by key"))
        matched-item)
      ;; No issue key - try to match by title (for replies without keys)
      (let [clean-subject (email-subject-to-issue-title subject)
            _ (println "   → No issue key found, trying title match for:" clean-subject)
            all-items (items/list-work-items settings project-id)
            matched-item (first (filter (fn [item]
                                          (let [matches? (= (str/lower-case (str (:name item)))
                                                            (str/lower-case clean-subject))]
                                            (when matches?
                                              (println "   ✓ MATCH FOUND by title:" (:name item)))
                                            matches?))
                                        all-items))]
        (when-not matched-item
          (println "   → No matching issue found by title, will create new issue"))
        matched-item))))



;; ============================================================================
;; Pyjama Tools
;; ============================================================================

(defn create-or-update-issue
  "Pyjama tool: Create or update Plane issue from email
  
  Input: Email observation with :from, :subject, :body, :date
  Output: {:issue-id <id> :action :created | :updated :title <title>}"
  [obs]
  (try
    (let [settings (plane/load-settings)
          ;; Get project from settings or use default/first
          project-id (or (:default-project settings)
                         (-> (projects/list-projects settings)
                             first
                             :id))

          ;; Analyze email
          email-data {:subject (:subject obs)
                      :body (:body obs)
                      :from (:from obs)
                      :timestamp (:date obs)}

          ;; DEBUG: Log the email body
          _ (println "\n🔍 DEBUG: Email body received:")
          _ (println "   Length:" (count (str (:body obs))))
          _ (println "   First 100 chars:" (subs (str (:body obs)) 0 (min 100 (count (str (:body obs))))))

          analysis (email-utils/analyze-email email-data)

          ;; DEBUG: Log the analysis result
          _ (println "\n🔍 DEBUG: Email analysis:")
          _ (println "   Enhanced description length:" (count (str (:enhanced-description analysis))))
          _ (println "   Priority:" (:priority-plane analysis))

          ;; Check if this is a follow-up to existing issue
          existing-issue (find-existing-issue settings project-id (:subject obs))]

      (if existing-issue
        ;; Update existing issue with comment
        (do
          (println "\n✓ Found existing issue:" (:name existing-issue))
          (let [;; Extract sender - already fixed in registry.clj
                from-str (str (:from obs))
                date-str (str (:date obs))
                ;; Clean up description - remove "Sent at:" line (handles \r\n and \n)
                desc-raw (str (:enhanced-description analysis))
                desc-str (-> desc-raw
                             (str/replace #"(\r?\n){2}Sent at: [^\r\n]+(\r?\n)" "\n")
                             (str/replace #"(\r?\n)Sent at: [^\r\n]+(\r?\n)" "\n")
                             str/trim)
                _ (println "\n🔍 DEBUG: Building comment:")
                _ (println "   From:" from-str)
                _ (println "   Description cleaned:" (subs desc-str 0 (min 150 (count desc-str))))
                comment (str "Email from " from-str " at " date-str ":\n\n" desc-str)

                ;; Check if priority should be updated
                new-priority (:priority-plane analysis)
                old-priority (:priority existing-issue)
                should-update-priority? (and new-priority
                                             (not= new-priority "none")
                                             (not= new-priority old-priority))]

            ;; Add comment
            (items/add-comment settings project-id (:id existing-issue) comment)

            ;; Update priority if detected in follow-up email
            (when should-update-priority?
              (println "   🔄 Updating priority:" old-priority "→" new-priority)
              (items/update-work-item settings project-id (:id existing-issue)
                                      {:priority new-priority}))

            {:issue-id (:id existing-issue)
             :action :updated
             :title (:name existing-issue)
             :comment-added true
             :priority (if should-update-priority? new-priority old-priority)
             :priority-updated should-update-priority?
             :project-id project-id
             :attachments (:attachments obs)  ; Pass through for upload step
             :has-attachments (:has-attachments obs)}))

        ;; Create new issue
        (let [issue-title (email-subject-to-issue-title (:subject obs))
              ;; Clean up description for new issues too
              desc-raw (str (:enhanced-description analysis))
              desc-clean (-> desc-raw
                             (str/replace #"(\r?\n){2}Sent at: [^\r\n]+(\r?\n)" "\n")
                             (str/replace #"(\r?\n)Sent at: [^\r\n]+(\r?\n)" "\n")
                             str/trim)
              _ (println "\n✓ Creating new issue:" issue-title)
              _ (println "   Description to be sent:" (subs desc-clean 0 (min 200 (count desc-clean))))
              created-issue (items/create-work-item settings
                                                    project-id
                                                    {:name issue-title
                                                     :description_html desc-clean  ; Plane uses description_html
                                                     :priority (:priority-plane analysis)})]
          (println "   ✓ Issue created with ID:" (:id created-issue))
          {:issue-id (:id created-issue)
           :action :created
           :title (:name created-issue)
           :priority (:priority created-issue)
           :team (get-in analysis [:assignee :team])
           :project-id project-id
           :attachments (:attachments obs)  ; Pass through for upload step
           :has-attachments (:has-attachments obs)})))

    (catch Exception e
      (println "\n❌ ERROR in create-or-update-issue:" (.getMessage e))
      {:error (.getMessage e)
       :subject (:subject obs)
       :action :error})))

(defn upload-attachments-tool
  "Pyjama tool: Upload email attachments to Plane issue
  
  Input: {:issue-id <id> :project-id <id> :attachments [{:filename <name> :path <path>}] :prefix-timestamp <bool>}
  Output: {:uploaded <count> :failed <count>}"
  [obs]
  (try
    (let [settings (plane/load-settings)
          project-id (or (:project-id obs)
                         (:default-project settings)
                         (-> (projects/list-projects settings)
                             first
                             :id))
          issue-id (:issue-id obs)
          attachments (:attachments obs)
          prefix-timestamp? (get obs :prefix-timestamp false)  ; Default to false

          results (for [att-file attachments]
                    (if-let [file-path (:path att-file)]
                      (let [filename (if prefix-timestamp?
                                       ;; Use timestamp-prefixed filename from temp file
                                       (.getName (java.io.File. file-path))
                                       ;; Use original filename
                                       (:filename att-file))]
                        (if (att/upload-attachment settings project-id issue-id file-path filename)
                          :success
                          :failed))
                      :failed))]

      {:uploaded (count (filter #(= % :success) results))
       :failed (count (filter #(= % :failed) results))
       :total (count results)
       :issue-id issue-id})

    (catch Exception e
      {:error (.getMessage e)
       :uploaded 0
       :failed (count (:attachments obs))
       :issue-id (:issue-id obs)})))

(defn list-work-items-tool
  "Pyjama tool: List all work items in the project
  
  Input: {} (uses default project from settings)
  Output: {:items [{:id :name :state ...}] :count <count>}"
  [obs]
  (try
    (let [settings (plane/load-settings)
          project-id (or (:project-id obs)
                         (:default-project settings)
                         (-> (projects/list-projects settings)
                             first
                             :id))
          work-items (items/list-work-items settings project-id)
          incomplete-items (filter #(not= (:state %) "8c715458-2c2a-4330-87d3-2b2c5e436e36") work-items)]

      {:items incomplete-items
       :count (count incomplete-items)
       :first-item (first incomplete-items)
       :project-id project-id})

    (catch Exception e
      {:error (.getMessage e)
       :items []
       :count 0})))

(defn update-work-item-state-tool
  "Pyjama tool: Update a work item's state
  
  Input: {:item-id <id> :state-id <state-uuid> :project-id <id>}
  Output: {:success <bool> :item-id <id>}"
  [obs]
  (try
    (let [settings (plane/load-settings)
          project-id (or (:project-id obs)
                         (:default-project settings)
                         (-> (projects/list-projects settings)
                             first
                             :id))
          item-id (:item-id obs)
          state-id (:state-id obs)

          updated (items/update-work-item settings
                                          project-id
                                          item-id
                                          {:state state-id})]

      {:success (boolean updated)
       :item-id item-id
       :state-id state-id})

    (catch Exception e
      {:error (.getMessage e)
       :success false
       :item-id (:item-id obs)})))


;; ============================================================================
;; Tool Registry (for Pyjama)
;; ============================================================================

(defn register-tools!
  "Register Plane tools with Pyjama registry
  Returns a map of tool definitions for use in agent EDN files"
  []
  {:create-or-update-issue {:fn create-or-update-issue
                            :description "Create or update Plane issue from email"}
   :upload-attachments {:fn upload-attachments-tool
                        :description "Upload email attachments to Plane issue"}
   :list-work-items {:fn list-work-items-tool
                     :description "List all work items in the project"}
   :update-work-item-state {:fn update-work-item-state-tool
                            :description "Update a work item's state"}})

(comment
  ;; Example usage in Pyjama agent
  (require '[plane-client.pyjama.tools :as plane-tools])

  ;; Test create-or-update-issue
  (plane-tools/create-or-update-issue
   {:from "user@example.com"
    :subject "[BUG] Test issue"
    :body "This is a test bug report"
    :date "2026-02-02T15:00:00Z"})

  ;; Test upload-attachments
  (plane-tools/upload-attachments-tool
   {:issue-id "some-uuid"
    :project-id "project-uuid"
    :attachments [{:filename "test.png" :path "./test.png"}]}))
