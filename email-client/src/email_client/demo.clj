(ns email-client.demo
  "Demonstration of email client capabilities"
  (:require [email-client.send :as send]
            [email-client.read :as read]
            [clojure-mail.core]))

(defn -main [& _args]
  (println "=== Email Client Demo ===\n")

  ;; Load settings
  (println "1. Loading email settings...")
  (let [settings (send/load-settings)]
    (if-not settings
      (do
        (println "\n❌ Cannot run demo without email settings!")
        (println "   Please add :email to your ~/secrets.edn:")
        (println "   {:email")
        (println "    {:smtp {:host ... :port ... :user ... :pass ... :tls true}")
        (println "     :imap {:host ... :port ... :user ... :pass ... :ssl true}")
        (println "     :defaults {:from ...}}}")
        (println)
        (println "   See README.md for complete setup instructions.")
        (System/exit 1))
      (do
        (println "   ✓ Settings loaded successfully\n")

        ;; Demo 1: List recent messages
        (println "2. Listing recent messages...")
        (try
          (let [messages (read/read-inbox settings {:limit 5})]
            (println (str "   ✓ Retrieved " (count messages) " message(s)"))
            (when (seq messages)
              (println "\n   Recent messages:")
              (doseq [msg (take 3 messages)]
                (println (str "   - " (:subject msg)
                              " from " (get-in msg [:from :address]))))))
          (catch Exception e
            (println "   ⚠ Error reading inbox:" (.getMessage e))))
        (println)

        ;; Demo 2: Check unread messages
        (println "3. Checking for unread messages...")
        (try
          (let [unread (read/read-unread settings {:limit 5})]
            (if (seq unread)
              (do
                (println (str "   ✓ You have " (count unread) " unread message(s)"))
                (doseq [msg unread]
                  (println (str "   - " (:subject msg)))))
              (println "   ℹ No unread messages")))
          (catch Exception e
            (println "   ⚠ Error checking unread:" (.getMessage e))))
        (println)

        ;; Demo 3: List available folders
        (println "4. Listing available folders...")
        (try
          (let [store (read/get-store settings)
                folders (clojure-mail.core/folders store)]
            (println (str "   ✓ Found " (count folders) " folder(s)"))
            (doseq [folder (take 5 folders)]
              (println (str "   - " folder))))
          (catch Exception e
            (println "   ⚠ Error listing folders:" (.getMessage e))))
        (println)

        ;; Demo 4: Send a test email (commented out by default)
        (println "5. Send email demo (skipped)")
        (println "   To send a test email, uncomment the send demo code")
        (println "   or use: clojure -M:send <to> <subject> <body>")

        (comment
          ;; Uncomment to actually send a test email
          (println "5. Sending a test email...")
          (try
            (let [result (send/send-simple-email
                          settings
                          "your-email@example.com"  ; Change this!
                          "Test from Clojure Email Client"
                          "This is a test message sent from the Clojure email client demo.")]
              (if (= :SUCCESS (:error result))
                (println "   ✓ Test email sent successfully!")
                (println "   ⚠ Failed to send:" (:message result))))
            (catch Exception e
              (println "   ⚠ Error sending email:" (.getMessage e)))))
        (println)

        (println "=== Demo Complete ===")
        (println)
        (println "📚 Available commands:")
        (println "   Send: clojure -M:send <to> <subject> <body>")
        (println "   Read: clojure -M:read list [limit]")
        (println "   Read: clojure -M:read unread [limit]")
        (println "   Read: clojure -M:read search <query>")
        (println "   Demo: clojure -M:demo")))))

(comment
  ;; Run the demo
  (-main)

  ;; Interactive examples
  ;; (already required at top of file)

  ;; Load settings
  (def settings (send/load-settings))

  ;; Read emails
  (def messages (read/read-inbox settings {:limit 5}))
  (def unread (read/read-unread settings {:limit 5}))

  ;; Search
  (def results (read/search-inbox settings "important"))

  ;; Send email
  (send/send-simple-email settings
                          "recipient@example.com"
                          "Hello"
                          "Test message"))
