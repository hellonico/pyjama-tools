(ns email-client.tools.registry
  "Register email tools with Pyjama framework"
  (:require [email-client.send :as send]
            [email-client.read :as read]
            [clojure-mail.message :as message]
            [clojure.string]
            [secrets.core :as secrets])
  (:import [javax.mail Folder Flags$Flag]
           [javax.mail.search FlagTerm]))

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

(defn- extract-from [email]
  (let [from-raw (:from email)]
    (cond
      (sequential? from-raw)
      (let [first-elem (first from-raw)]
        (if (map? first-elem)
          (:address first-elem)
          (str first-elem)))
      (map? from-raw)
      (:address from-raw)
      :else (str from-raw))))

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
                            :from (extract-from email)
                            :date (str (:date-sent email))
                            :id (:id email)})
                         emails)
           :count (count emails)})
        (catch Exception e
          {:error (str "Failed to read: " (.getMessage e))})))))

(defn watch-emails
  "Watch for new emails (streaming)
   
   Args:
     :interval-ms - polling interval in milliseconds (default 5000)
     :mark-read - mark message as read after retrieving (default true)
     :limit - maximum number of emails to retrieve (default 10)"
  [{:keys [_interval-ms mark-read limit]
    :or {mark-read true limit 10}}]
  (let [settings (secrets/require-secret! :email)]
    (if-not settings
      {:error "Email settings not found in secrets"}
      (try
        (println "🔍 Getting email store...")
        ;; Get store and folder with READ_WRITE access
        (let [store (read/get-store settings)
              _ (println "✓ Got store, getting INBOX folder...")
              folder (.getFolder store "INBOX")]
          (println "✓ Got folder, opening with READ_WRITE...")
          (.open folder Folder/READ_WRITE)
          (println "✓ Folder opened, searching for unread messages...")
          (try
            (let [;; Use IMAP SEARCH to fetch only UNSEEN messages - much faster!
                  search-term (javax.mail.search.FlagTerm.
                               (javax.mail.Flags. Flags$Flag/SEEN)
                               false)  ; false = NOT SEEN
                  messages (.search folder search-term)
                  _ (println (str "✓ Found " (alength messages) " unread messages"))
                  ;; Limit the number of messages to process
                  unread-msgs (vec (take limit messages))]
              (if (seq unread-msgs)
                (let [emails (mapv (fn [unread-msg]
                                     ;; Mark as read if requested
                                     (when mark-read
                                       (.setFlags unread-msg (doto (javax.mail.Flags.)
                                                               (.add Flags$Flag/SEEN)) true))
                                     ;; Extract and save attachments to temp files
                                     (let [email (message/read-message unread-msg)
                                           attachments (read/save-attachments email)
                                           body (read/get-message-body email)
                                           from-str (extract-from email)]
                                       (when (seq attachments)
                                         (println (str "📎 Found " (count attachments) " attachment(s) in: " (:subject email))))
                                       ;; DEBUG: Log the extracted body
                                       (println (str "\n📧 Email extracted:"))
                                       (println (str "   From: " from-str))
                                       (println (str "   Subject: " (:subject email)))
                                       (println (str "   Body length: " (count (str body))))
                                       (println (str "   Body preview: " (subs (str body) 0 (min 100 (count (str body))))))
                                       {:subject (:subject email)
                                        :from from-str
                                        :date (str (:date-sent email))
                                        :body body
                                        :attachments attachments
                                        :has-attachments (boolean (seq attachments))}))
                                   unread-msgs)]
                  (println (str "✓ Retrieved " (count emails) " unread email(s)"))
                  (when mark-read
                    (println (str "✓ Marked " (count emails) " email(s) as read")))
                  {:emails emails
                   :count (count emails)})
                {:emails []
                 :count 0
                 :message "No new emails"}))
            (finally
              (.close folder false)
              (.close store))))
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
    :description "Watch for new emails. Returns all unread emails (up to limit). Use with loop construct to process multiple emails in batch."
    :parameters {:type "object"
                 :properties {:interval-ms {:type "integer"
                                            :description "Polling interval in milliseconds"
                                            :default 5000}
                              :mark-read {:type "boolean"
                                          :description "Mark retrieved emails as read"
                                          :default true}
                              :limit {:type "integer"
                                      :description "Maximum number of emails to retrieve"
                                      :default 10}}
                 :required []}
    :function watch-emails}})
