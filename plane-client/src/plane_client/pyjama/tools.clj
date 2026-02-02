(ns plane-client.pyjama.tools
  "Pyjama tools for Plane API operations - for use with jetlag and other agents"
  (:require [plane-client.core :as plane]
            [plane-client.projects :as projects]
            [plane-client.work-items :as items]
            [plane-client.attachments :as att]
            [plane-client.email-utils :as email-utils]
            [clojure.string :as str]))

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn extract-issue-key
  "Extract issue key from email subject (text between [brackets])"
  [subject]
  (when-let [match (re-find #"\[(.*?)\]" subject)]
    (second match)))

(defn find-existing-issue
  "Find an existing work item by matching subject pattern"
  [settings project-id subject]
  (let [issue-key (extract-issue-key subject)
        all-items (items/list-work-items settings project-id)]
    (when issue-key
      (first (filter #(str/includes? (:name %) issue-key) all-items)))))

(defn email-subject-to-issue-title
  "Convert email subject to issue title"
  [subject]
  (-> subject
      (str/replace #"^RE:\s*" "")
      str/trim))

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
          analysis (email-utils/analyze-email email-data)

          ;; Check if this is a follow-up to existing issue
          existing-issue (find-existing-issue settings project-id (:subject obs))]

      (if existing-issue
        ;; Update existing issue with comment
        (let [comment (str "Email from " (:from obs) " at " (:date obs) ":\n\n"
                           (:enhanced-description analysis))]
          (items/add-comment settings project-id (:id existing-issue) comment)
          {:issue-id (:id existing-issue)
           :action :updated
           :title (:name existing-issue)
           :comment-added true
           :priority (:priority existing-issue)
           :project-id project-id
           :attachments (:attachments obs)  ; Pass through for upload step
           :has-attachments (:has-attachments obs)})

        ;; Create new issue
        (let [issue-title (email-subject-to-issue-title (:subject obs))
              created-issue (items/create-work-item settings
                                                    project-id
                                                    {:name issue-title
                                                     :description (:enhanced-description analysis)
                                                     :priority (:priority-plane analysis)})]
          {:issue-id (:id created-issue)
           :action :created
           :title (:name created-issue)
           :priority (:priority created-issue)
           :team (get-in analysis [:assignee :team])
           :project-id project-id
           :attachments (:attachments obs)  ; Pass through for upload step
           :has-attachments (:has-attachments obs)})))

    (catch Exception e
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
          work-items (items/list-work-items settings project-id)]

      {:items work-items
       :count (count work-items)
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
