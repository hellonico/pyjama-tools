(ns email-client.send
  "Email sending functionality using postal library"
  (:require [postal.core :as postal]
            [clojure.string]
            [secrets.core :as secrets]))

;; ============================================================================
;; Configuration Loading
;; ============================================================================

(defn load-settings
  "Load email settings using the secrets library.

  The secrets library automatically checks (in priority order):
  1. ./secrets.edn (project local)
  2. ./secrets.edn.enc (project encrypted)
  3. ~/secrets.edn (user home)
  4. ~/secrets.edn.enc (user home encrypted)
  5. Environment variables (EMAIL__* prefix)

  Example secrets.edn:
  {:email
   {:smtp {:host \"smtp.gmail.com\" :port 587 :user \"...\" :pass \"...\" :tls true}
    :imap {:host \"imap.gmail.com\" :port 993 :user \"...\" :pass \"...\" :ssl true}
    :defaults {:from \"...\"}}}"
  []
  (secrets/require-secret! :email))

(defn get-smtp-config
  "Extract SMTP connection config from settings map"
  [settings]
  (let [smtp (:smtp settings)]
    (cond-> {:host (:host smtp)
             :port (:port smtp)}
      (:user smtp) (assoc :user (:user smtp))
      (:pass smtp) (assoc :pass (:pass smtp))
      (:tls smtp) (assoc :tls (:tls smtp))
      (:ssl smtp) (assoc :ssl (:ssl smtp)))))

;; ============================================================================
;; Email Sending
;; ============================================================================

(defn send-email
  "Send an email using postal.

  Parameters:
  - settings: Email settings map (from load-settings)
  - message: Email message map with keys:
    - :to      - Recipient(s) (string or vector of strings)
    - :subject - Email subject
    - :body    - Email body (plain text or map for multipart)
    - :from    - Sender (optional, uses default from settings)
    - :cc      - CC recipients (optional)
    - :bcc     - BCC recipients (optional)
    - :attachments - Vector of attachment maps (optional)

  Returns: Result map with :error, :code, :message"
  [settings message]
  (let [smtp-config (get-smtp-config settings)
        defaults (:defaults settings)
        full-message (cond-> message
                       (and (not (:from message)) (:from defaults))
                       (assoc :from (:from defaults))

                       (and (not (:reply-to message)) (:reply-to defaults))
                       (assoc :reply-to (:reply-to defaults)))]

    (println "📨 Sending email to:" (:to full-message))
    (println "   Subject:" (:subject full-message))

    (try
      (let [result (postal/send-message smtp-config full-message)]
        (if (= :SUCCESS (:error result))
          (println "✓ Email sent successfully!")
          (println "✗ Failed to send email:" (:message result)))
        result)
      (catch Exception e
        (println "✗ Error sending email:" (.getMessage e))
        {:error :EXCEPTION
         :code -1
         :message (.getMessage e)
         :exception e}))))

(defn send-simple-email
  "Send a simple plain-text email.

  Parameters:
  - settings: Email settings map
  - to: Recipient email address (string or vector)
  - subject: Email subject
  - body: Email body text"
  [settings to subject body]
  (send-email settings
              {:to to
               :subject subject
               :body body}))

(defn send-html-email
  "Send an HTML email.

  Parameters:
  - settings: Email settings map
  - to: Recipient email address (string or vector)
  - subject: Email subject
  - html-body: HTML content"
  [settings to subject html-body]
  (send-email settings
              {:to to
               :subject subject
               :body [{:type "text/html"
                       :content html-body}]}))

(defn send-email-with-attachments
  "Send an email with attachments.

  Parameters:
  - settings: Email settings map
  - to: Recipient email address
  - subject: Email subject
  - body: Email body text
  - attachments: Vector of attachment maps with:
    - :type - MIME type (e.g., \"application/pdf\")
    - :content - File path or java.io.File
    - :file-name - Optional display name"
  [settings to subject body attachments]
  (send-email settings
              {:to to
               :subject subject
               :body body
               :attachments attachments}))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn -main [& args]
  (println "=== Email Sender ===\n")

  (let [settings (load-settings)]
    (if-not settings
      (System/exit 1)
      (let [[to subject & body-parts] args]
        (cond
          (empty? args)
          (do
            (println "Usage: clojure -M:send <to> <subject> <body...>")
            (println)
            (println "Examples:")
            (println "  clojure -M:send you@example.com \"Hello\" \"This is a test\"")
            (println "  clojure -M:send person@example.com \"Meeting\" \"Let's meet tomorrow\"")
            (System/exit 1))

          (not to)
          (do
            (println "Error: Recipient email required")
            (System/exit 1))

          (not subject)
          (do
            (println "Error: Subject required")
            (System/exit 1))

          :else
          (let [body (clojure.string/join " " body-parts)
                result (send-simple-email settings to subject body)]
            (if (= :SUCCESS (:error result))
              (System/exit 0)
              (System/exit 1))))))))

(comment
  ;; Examples - try these in the REPL

  ;; 1. Load settings
  (def settings (load-settings))

  ;; 2. Send a simple email
  (send-simple-email settings
                     "recipient@example.com"
                     "Test Email"
                     "This is a test message from Clojure postal!")

  ;; 3. Send to multiple recipients
  (send-simple-email settings
                     ["person1@example.com" "person2@example.com"]
                     "Group Email"
                     "This goes to multiple people")

  ;; 4. Send HTML email
  (send-html-email settings
                   "recipient@example.com"
                   "HTML Email Test"
                   "<h1>Hello!</h1><p>This is <strong>HTML</strong> email.</p>")

  ;; 5. Send with CC and BCC
  (send-email settings
              {:to "primary@example.com"
               :cc "cc@example.com"
               :bcc "bcc@example.com"
               :subject "Email with CC/BCC"
               :body "Testing CC and BCC"})

  ;; 6. Send with attachment (requires :require [clojure.java.io :as io])
  (send-email-with-attachments settings
                               "recipient@example.com"
                               "Document Attached"
                               "Please see attached document."
                               [{:type "application/pdf"
                                 :content (clojure.java.io/file "document.pdf")
                                 :file-name "report.pdf"}])

  ;; Run from CLI
  (-main "test@example.com" "CLI Test" "This is from command line"))
