(ns email-client.watch-poll
  "Email watching server using polling (more reliable than IDLE)"
  (:require [email-client.read :as read]
            [email-client.watch :refer [apply-rules default-callback]]
            [secrets.core :as secrets]))

;; ============================================================================
;; Polling-Based Watcher
;; ============================================================================

(defn start-polling
  "Start polling for new emails and trigger callbacks on rule matches.
  
  Parameters:
  - settings: Email settings map
  - rules: Vector of [rule callback] pairs
  - opts: Options map:
    - :folder - Folder to watch (default from settings or \"INBOX\")
    - :interval-ms - Polling interval (default from settings or 5000ms)
    - :on-start - Callback when watching starts
    - :on-error - Callback (fn [error] ...) for errors
  
  Returns: Atom with {:running? true :store store} (use with stop-polling)"
  [settings rules & [opts]]
  (let [watcher-config (get settings :watcher {})
        folder (or (:folder opts) (:folder watcher-config) "INBOX")
        interval-ms (or (:interval-ms opts) (:interval-ms watcher-config) 5000)]

    (println (str "🎧 Starting email watcher on " folder "..."))
    (println (str "   Polling every " (/ interval-ms 1000) " seconds"))
    (println "   📭 Watching for UNREAD emails only")

    ;; Create store once and reuse it
    (let [store (read/get-store settings)
          state (atom {:running? true
                       :seen-ids #{}
                       :store store})

          ;; Helper to extract email from InternetAddress
          extract-email
          (fn [addr-obj]
            (if (nil? addr-obj)
              "Unknown"
              (try
                (if (string? addr-obj)
                  addr-obj
                  (.toString (.getAddress addr-obj)))
                (catch Exception _
                  (str addr-obj)))))

          poll-fn
          (fn []
            (try
              (let [mail-folder (.getFolder store folder)]
                (.open mail-folder javax.mail.Folder/READ_WRITE)
                ;; Search for UNREAD messages only
                (let [search-term (javax.mail.search.FlagTerm.
                                   (javax.mail.Flags. javax.mail.Flags$Flag/SEEN)
                                   false)
                      messages (vec (.search mail-folder search-term))
                      seen-ids (:seen-ids @state)
                      ;; Use Message-ID header for reliable deduplication
                      new-messages (filter (fn [msg]
                                             (let [msg-id (try
                                                            (first (.getHeader msg "Message-ID"))
                                                            (catch Exception _
                                                              (str (.getSubject msg) "-" (.getSentDate msg))))]
                                               (not (contains? seen-ids msg-id))))
                                           messages)]

                  ;; Update seen IDs with Message-IDs
                  (swap! state update :seen-ids into
                         (map (fn [msg]
                                (try
                                  (first (.getHeader msg "Message-ID"))
                                  (catch Exception _
                                    (str (.getSubject msg) "-" (.getSentDate msg)))))
                              messages))

                  ;; Process new messages
                  (doseq [msg new-messages]
                    (let [from-addrs (.getFrom msg)
                          from-email (when from-addrs
                                       (extract-email (first from-addrs)))
                          parsed-msg {:subject (.getSubject msg)
                                      :from from-email
                                      :from-raw from-addrs
                                      :to (.getAllRecipients msg)
                                      :date-sent (.getSentDate msg)
                                      :id (try (first (.getHeader msg "Message-ID"))
                                               (catch Exception _ nil))
                                      :raw-message msg}
                          matches (apply-rules rules parsed-msg)]
                      (doseq [[_rule callback _msg] matches]
                        (try
                          (callback parsed-msg)
                          (catch Exception e
                            (println "⚠ Error in callback:" (.getMessage e))
                            (when (:on-error opts) ((:on-error opts) e))))))

                    ;; Mark message as read after processing
                    (try
                      (.setFlag msg javax.mail.Flags$Flag/SEEN true)
                      (catch Exception e
                        (println "⚠ Warning: Could not mark message as read:" (.getMessage e)))))

                  ;; Close folder after reading
                  (.close mail-folder false)))

              (catch Exception e
                (println "⚠ Error polling for messages:" (.getMessage e))
                (when (:on-error opts) ((:on-error opts) e)))))

          worker-thread
          (Thread.
           (fn []
             (when (:on-start opts) ((:on-start opts)))
             (println "✓ Email watcher started. Polling for new unread messages...")
             (println "  Press Ctrl+C to stop.")

             (while (:running? @state)
               (poll-fn)
               (Thread/sleep interval-ms)))
           "email-watcher-thread")]

      (.start worker-thread)
      state)))

(defn stop-polling
  "Stop polling for emails and close the connection"
  [watcher]
  (println "\n🛑 Stopping email watcher...")
  (swap! watcher assoc :running? false)
  (Thread/sleep 500)  ; Give poll time to finish
  (when-let [store (:store @watcher)]
    (try
      (.close store)
      (catch Exception _e nil)))
  (println "✓ Email watcher stopped."))

(defn watch-with-rules
  "Watch emails with rules using polling. Blocks until interrupted"
  [settings rules & [opts]]
  (let [watcher (start-polling settings rules opts)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(stop-polling watcher)))
    (while (:running? @watcher)
      (Thread/sleep 1000))))

(defn watch-all
  "Watch all new emails with default callback using polling"
  [settings & [opts]]
  (watch-with-rules settings
                    [[{:all true} default-callback]]
                    opts))

(defn -main [& args]
  (println "=== Email Watcher (Polling Mode) ===\n")

  (let [settings (secrets/require-secret! :email)]
    (case (first args)
      "all"
      (do
        (println "Watching all emails...")
        (watch-all settings))

      "subject"
      (if-let [pattern (second args)]
        (do
          (println (str "Watching for subject containing: " pattern))
          (watch-with-rules settings
                            [[{:subject pattern}
                              #(do
                                 (println "\n🎯 MATCHED!")
                                 (default-callback %))]]))
        (println "Error: Subject pattern required"))

      "from"
      (if-let [pattern (second args)]
        (do
          (println (str "Watching for emails from: " pattern))
          (watch-with-rules settings
                            [[{:from pattern}
                              #(do
                                 (println "\n🎯 MATCHED!")
                                 (default-callback %))]]))
        (println "Error: From pattern required"))

      ;; Default
      (do
        (println "Usage:")
        (println "  clojure -M:watch-poll all")
        (println "  clojure -M:watch-poll subject <text>")
        (println "  clojure -M:watch-poll from <email>")
        (System/exit 1)))))
