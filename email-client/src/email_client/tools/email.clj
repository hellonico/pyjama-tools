(ns email-client.tools.email
  "Pyjama tool wrappers for email operations"
  (:require [email-client.send :as send]
            [email-client.read :as read]
            [email-client.watch-poll :as watch]
            [secrets.core :as secrets]))

;; ============================================================================
;; Tool: Send Email
;; ============================================================================

(defn send-email-tool
  "Pyjama tool for sending emails.
  
  Parameters (from agent):
  - to: Email address to send to
  - subject: Email subject
  - body: Email body text
  
  Returns: Map with :success and :message"
  [{:keys [to subject body]}]
  (try
    (let [settings (secrets/require-secret! :email)]
      (if-not settings
        {:success false
         :message "Email settings not found in secrets.edn"}
        (do
          (send/send-simple-email settings to subject body)
          {:success true
           :message (str "Email sent to " to)
           :to to
           :subject subject})))
    (catch Exception e
      {:success false
       :message (str "Failed to send email: " (.getMessage e))})))

;; ============================================================================
;; Tool: Read Emails
;; ============================================================================

(defn read-emails-tool
  "Pyjama tool for reading emails.
  
  Parameters (from agent):
  - folder: Folder name (default \"INBOX\")
  - limit: Max number of emails to read (default 10)
  - unread-only: If true, only read unread emails
  
  Returns: Vector of email maps"
  [{:keys [folder limit unread-only]
    :or {folder "INBOX" limit 10 unread-only false}}]
  (try
    (let [settings (secrets/require-secret! :email)]
      (if-not settings
        {:success false
         :message "Email settings not found in secrets.edn"
         :emails []}
        (let [emails (if unread-only
                       (read/read-unread settings {:limit limit :folder folder})
                       (read/read-inbox settings {:limit limit :folder folder}))]
          {:success true
           :count (count emails)
           :emails (mapv (fn [email]
                           {:subject (:subject email)
                            :from (str (:from email))
                            :date (:date-sent email)
                            :preview (read/get-message-body email)})
                         emails)})))
    (catch Exception e
      {:success false
       :message (str "Failed to read emails: " (.getMessage e))
       :emails []})))

;; ============================================================================
;; Tool: Watch for Emails (Callback-based)
;; ============================================================================

(defn watch-emails-tool
  "Pyjama tool for watching emails with a callback.
  
  This is designed to be used with Pyjama's streaming/callback system.
  
  Parameters:
  - callback: Function (fn [email] ...) called for each new email
  - rules: Optional vector of [rule callback] pairs (default: all emails)
  - interval-ms: Polling interval (default from settings)
  
  Returns: Watcher state atom (call stop-polling to stop)"
  [{:keys [callback rules interval-ms]}]
  (try
    (let [settings (secrets/require-secret! :email)
          watcher-rules (or rules
                            [[{:all true}
                              (fn [msg]
                                (when callback
                                  (callback {:subject (:subject msg)
                                             :from (:from msg)
                                             :date (:date-sent msg)
                                             :body (when-let [raw (:raw-message msg)]
                                                     (try
                                                       (str (.getContent raw))
                                                       (catch Exception _ nil)))})))]])]
      (if-not settings
        {:success false
         :message "Email settings not found in secrets.edn"}
        (let [watcher (watch/start-polling settings
                                           watcher-rules
                                           (when interval-ms {:interval-ms interval-ms}))]
          {:success true
           :message "Email watcher started"
           :watcher watcher})))
    (catch Exception e
      {:success false
       :message (str "Failed to start email watcher: " (.getMessage e))})))

;; ============================================================================
;; Tool Definitions for Pyjama
;; ============================================================================

(def email-tools
  "Email tools for Pyjama framework"
  {:send-email
   {:name "send-email"
    :description "Send an email to a recipient"
    :parameters {:type "object"
                 :properties {:to {:type "string"
                                   :description "Recipient email address"}
                              :subject {:type "string"
                                        :description "Email subject"}
                              :body {:type "string"
                                     :description "Email body content"}}
                 :required ["to" "subject" "body"]}
    :function send-email-tool}

   :read-emails
   {:name "read-emails"
    :description "Read emails from inbox or specified folder"
    :parameters {:type "object"
                 :properties {:folder {:type "string"
                                       :description "Folder name (default INBOX)"}
                              :limit {:type "integer"
                                      :description "Maximum number of emails to read"}
                              :unread-only {:type "boolean"
                                            :description "Only read unread emails"}}
                 :required []}
    :function read-emails-tool}

   :watch-emails
   {:name "watch-emails"
    :description "Watch for new emails and trigger callback for each"
    :parameters {:type "object"
                 :properties {:interval-ms {:type "integer"
                                            :description "Polling interval in milliseconds"}}
                 :required []}
    :function watch-emails-tool}})
