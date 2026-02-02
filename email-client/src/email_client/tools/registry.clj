(ns email-client.tools.registry
  "Register email tools with Pyjama framework"
  (:require [email-client.send :as send]
            [email-client.read :as read]
            [secrets.core :as secrets]))

;; ============================================================================
;; Tool Implementations
;; ============================================================================

(defn send-email
  "Send an email
  
  Args:
    :to - recipient email
    :subject - email subject  
    :body - email body"
  [{:keys [to subject body]}]
  (let [settings (secrets/require-secret! :email)]
    (if-not settings
      {:error "Email settings not found in secrets"}
      (try
        (send/send-simple-email settings to subject body)
        {:success true
         :message (str "Email sent to " to)}
        (catch Exception e
          {:error (str "Failed to send: " (.getMessage e))})))))

(defn read-emails
  "Read emails from inbox
  
  Args:
    :folder - folder name (default INBOX)
    :limit - max emails to read (default 10)
    :unread-only - only read unread emails (default false)"
  [{:keys [folder limit unread-only]
    :or {folder "INBOX" limit 10 unread-only false}}]
  (let [settings (secrets/require-secret! :email)]
    (if-not settings
      {:error "Email settings not found in secrets"}
      (try
        (let [emails (if unread-only
                       (read/read-unread settings {:limit limit :folder folder})
                       (read/read-inbox settings {:limit limit :folder folder}))]
          {:emails (mapv (fn [email]
                           {:subject (:subject email)
                            :from (str (:from email))
                            :date (str (:date-sent email))
                            :id (:id email)})
                         emails)
           :count (count emails)})
        (catch Exception e
          {:error (str "Failed to read: " (.getMessage e))})))))

(defn watch-emails
  "Watch for new emails (streaming)
  
  Args:
    :interval-ms - polling interval in milliseconds (default 5000)"
  [{:keys [_interval-ms]}]
  (let [settings (secrets/require-secret! :email)]
    (if-not settings
      {:error "Email settings not found in secrets"}
      (try
        ;; Start watcher - returns immediately with first email found
        (let [emails (read/read-unread settings {:limit 1})
              email (first emails)]
          (if email
            {:subject (:subject email)
             :from (str (:from email))
             :date (str (:date-sent email))
             :body (read/get-message-body email)}
            {:message "No new emails"}))
        (catch Exception e
          {:error (str "Failed to watch: " (.getMessage e))})))))

;; ============================================================================
;; Tool Definitions (Pyjama Format)
;; ============================================================================

(def tools
  {:send-email
   {:name "send-email"
    :description "Send an email to a recipient with subject and body"
    :parameters {:type "object"
                 :properties {:to {:type "string"
                                   :description "Recipient email address"}
                              :subject {:type "string"
                                        :description "Email subject line"}
                              :body {:type "string"
                                     :description "Email message body"}}
                 :required ["to" "subject" "body"]}
    :function send-email}

   :read-emails
   {:name "read-emails"
    :description "Read emails from inbox. Returns a list of recent emails with subject, sender, and date."
    :parameters {:type "object"
                 :properties {:folder {:type "string"
                                       :description "Email folder to read from"
                                       :default "INBOX"}
                              :limit {:type "integer"
                                      :description "Maximum number of emails to retrieve"
                                      :default 10}
                              :unread-only {:type "boolean"
                                            :description "Only return unread emails"
                                            :default false}}
                 :required []}
    :function read-emails}

   :watch-emails
   {:name "watch-emails"
    :description "Start watching for new emails. This will stream new emails as they arrive. The watcher runs continuously until stopped."
    :parameters {:type "object"
                 :properties {:interval-ms {:type "integer"
                                            :description "Polling interval in milliseconds"
                                            :default 5000}}
                 :required []}
    :streaming true
    :function watch-emails}})
