(ns plane-client.pyjama.email-confirmation
  "Send confirmation emails when issues are created via email"
  (:require [secrets.core :as secrets]))

(defn send-confirmation-email
  "Send a confirmation email to the sender with the issue key in the subject.
  
  This enables email threading - when the sender replies, Gmail will include
  the issue key in the 'Re:' subject, allowing the system to add comments
  instead of creating new issues.
  
  Parameters:
  - sender-email: Email address of the person who sent the original email
  - issue-key: The Plane issue key (e.g., 'EDEMO-60')
  - issue-title: The title of the created issue
  - issue-data: Map with :priority, :id, :project-id, etc.
  
  Returns: true if email sent successfully, false otherwise"
  [sender-email issue-key issue-title issue-data]
  (try
    (let [email-settings (secrets/require-secret! :email)]
      (when email-settings
        ;; Dynamically require email-client.send to avoid circular dependencies
        (require '[email-client.send :as email-send])
        (let [send-fn (resolve 'email-client.send/send-simple-email)
              plane-settings (secrets/require-secret! :plane)
              plane-url (or (:base-url plane-settings) "https://plane.example.com")
              workspace (or (:workspace-slug plane-settings) "workspace")
              project-id (:project-id issue-data)
              issue-id (:id issue-data)
              issue-url (str plane-url "/" workspace "/projects/" project-id "/issues/" issue-id)

              subject (str "[" issue-key "] " issue-title)
              body (str "Your issue has been created in Plane!\n\n"
                        "Issue: " issue-key "\n"
                        "Title: " issue-title "\n"
                        "Priority: " (or (:priority issue-data) "none") "\n"
                        "Status: Created\n\n"
                        "View your issue: " issue-url "\n\n"
                        "---\n"
                        "Reply to this email to add comments to the issue.\n"
                        "The issue key [" issue-key "] in the subject line ensures your reply\n"
                        "is added as a comment instead of creating a new issue.")]

          (println "\n📧 Sending confirmation email to:" sender-email)
          (println "   Subject:" subject)

          (let [result (send-fn email-settings sender-email subject body)]
            (if (= :SUCCESS (:error result))
              (do
                (println "   ✓ Confirmation email sent!")
                true)
              (do
                (println "   ✗ Failed to send confirmation:" (:message result))
                false))))))
    (catch Exception e
      (println "   ✗ Error sending confirmation email:" (.getMessage e))
      false)))

(defn build-issue-key
  "Build the full issue key from project identifier and sequence ID.
  
  Example: project-identifier='EDEMO', sequence-id=60 → 'EDEMO-60'"
  [issue]
  (when (and (:project_identifier issue) (:sequence_id issue))
    (str (:project_identifier issue) "-" (:sequence_id issue))))
