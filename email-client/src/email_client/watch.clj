(ns email-client.watch
  "Email watching server with rule-based callbacks"
  (:require [clojure.string :as str]
            [clojure-mail.core :as mail]
            [clojure-mail.message :as message]
            [clojure-mail.events :as events]
            [email-client.read :as read]
            [secrets.core :as secrets]))

;; ============================================================================
;; Rule Matching
;; ============================================================================

(defn match-rule?
  "Check if a message matches a rule.
  
  Rules can be:
  - {:subject \"pattern\"} - Subject contains pattern (case-insensitive)
  - {:from \"email@example.com\"} - From address contains pattern
  - {:to \"email@example.com\"} - To address contains pattern
  - {:body \"pattern\"} - Body contains pattern (case-insensitive)
  - {:all true} - Matches all messages
  - Function: (fn [msg] ...) - Custom predicate
  
  Multiple conditions are AND'd together."
  [rule msg]
  (cond
    ;; Function rule - custom predicate
    (fn? rule)
    (rule msg)

    ;; Map rule - check all conditions
    (map? rule)
    (every? (fn [[k v]]
              (case k
                :subject
                (when-let [subject (:subject msg)]
                  (str/includes? (str/lower-case subject) (str/lower-case v)))

                :from
                (let [from-addr (or (get-in msg [:from :address])
                                    (get-in msg [:from :name])
                                    (str (:from msg)))]
                  (when from-addr
                    (str/includes? (str/lower-case from-addr) (str/lower-case v))))

                :to
                (when-let [to-addrs (:to msg)]
                  (some #(str/includes? (str/lower-case (or (:address %) (str %)))
                                        (str/lower-case v))
                        to-addrs))

                :body
                (when-let [body (read/get-message-body msg)]
                  (str/includes? (str/lower-case body) (str/lower-case v)))

                :all
                v

                ;; Unknown key - ignore
                true))
            rule)

    ;; Default - no match
    :else false))

(defn apply-rules
  "Apply rules to a message and return matched rule-callback pairs.
  
  rules is a vector of [rule callback] pairs.
  Returns vector of [rule callback msg] for all matching rules."
  [rules msg]
  (for [[rule callback] rules
        :when (match-rule? rule msg)]
    [rule callback msg]))

;; ============================================================================
;; Default Callback
;; ============================================================================

(defn preview-text
  "Get preview of text (first N words)"
  [text n]
  (when text
    (let [words (str/split text #"\s+")
          preview (take n words)]
      (str (str/join " " preview)
           (when (> (count words) n) "...")))))

(defn default-callback
  "Default callback that prints subject and message preview"
  [msg]
  (println "\n📧 New Email Received!")
  (println "───────────────────────────────────────")
  ;; Handle both JavaMail Address objects and parsed messages
  (let [from-val (:from msg)
        from-str (cond
                   (string? from-val) from-val
                   (map? from-val) (or (:address from-val) (:name from-val))
                   (sequential? from-val) (if (empty? from-val)
                                            "Unknown"
                                            (let [addr (first from-val)]
                                              (if (map? addr)
                                                (or (:address addr) (str addr))
                                                (str addr))))
                   :else (str from-val))]
    (println "From:" from-str))
  (println "Date:" (:date-sent msg))
  (println "Subject:" (:subject msg))
  ;; Try to get body if available
  (when-let [body (or (read/get-message-body msg)
                      (when-let [raw (:raw-message msg)]
                        (try
                          (.getContent raw)
                          (catch Exception _ nil))))]
    (when (string? body)
      (println "Preview:" (preview-text body 15))))
  (println "───────────────────────────────────────\n"))

;; ============================================================================
;; Email Watching
;; ============================================================================

(defn start-watching
  "Start watching for new emails and trigger callbacks on rule matches.
  
  Parameters:
  - settings: Email settings map
  - rules: Vector of [rule callback] pairs
    - rule: Map or function to match messages (see match-rule?)
    - callback: Function (fn [msg] ...) called when rule matches
  - opts: Options map:
    - :folder - Folder to watch (default \"INBOX\")
    - :on-start - Callback when watching starts
    - :on-error - Callback (fn [error] ...) for errors
  
  Returns: Manager object (use with stop-watching)
  
  Example:
  (start-watching settings
                  [[{:subject \"urgent\"} urgent-handler]
                   [{:from \"boss@company.com\"} boss-handler]
                   [{:all true} default-callback]])"
  [settings rules & [{:keys [folder on-start on-error]
                      :or {folder "INBOX"}}]]
  (println (str "🎧 Starting email watcher on " folder "..."))

  (let [imap (:imap settings)
        session (read/get-imap-session settings)
        store (.getStore session (if (:ssl imap) "imaps" "imap"))]

    ;; Connect to store
    (.connect store (:host imap) (:user imap) (:pass imap))

    (let [mail-folder (mail/open-folder store folder :readonly)
          manager (events/new-idle-manager session)]

      ;; Add message count listener
      (events/add-message-count-listener
       (fn [event]
         (try
           (let [messages (:messages event)
                 parsed-msgs (mapv message/read-message messages)]
             (doseq [msg parsed-msgs]
               (let [matches (apply-rules rules msg)]
                 (doseq [[_rule callback _] matches]
                   (try
                     (callback msg)
                     (catch Exception e
                       (println "⚠ Error in callback:" (.getMessage e))
                       (when on-error (on-error e))))))))
           (catch Exception e
             (println "⚠ Error processing messages:" (.getMessage e))
             (when on-error (on-error e)))))

       (fn [event]
         (println "📭 Messages removed:" (count (:messages event))))

       mail-folder
       manager)

      (when on-start (on-start))
      (println "✓ Email watcher started. Listening for new messages...")
      (println "  Press Ctrl+C to stop.")

      ;; Return manager for stopping
      {:manager manager
       :store store
       :folder mail-folder})))

(defn stop-watching
  "Stop watching for emails.
  
  Parameters:
  - watcher: Manager object returned from start-watching"
  [{:keys [manager store folder]}]
  (println "\n🛑 Stopping email watcher...")
  (when manager
    (events/stop manager))
  (when folder
    (.close folder false))
  (when store
    (.close store))
  (println "✓ Email watcher stopped."))

;; ============================================================================
;; Convenience Functions
;; ============================================================================

(defn watch-with-rules
  "Watch emails with rules. Blocks until interrupted.
  
  Parameters:
  - settings: Email settings
  - rules: Vector of [rule callback] pairs
  
  Example:
  (watch-with-rules settings
    [[{:subject \"urgent\"} #(println \"URGENT:\" (:subject %))]
     [{:from \"boss@\"} #(println \"From boss:\" (:subject %))]
     [{:all true} default-callback]])"
  [settings rules]
  (let [watcher (start-watching settings rules)]
    ;; Add shutdown hook
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(stop-watching watcher)))
    ;; Keep alive
    (while true
      (Thread/sleep 1000))))

(defn watch-all
  "Watch all new emails with default callback.
  
  Just prints subject and preview of each new email."
  [settings]
  (watch-with-rules settings
                    [[{:all true} default-callback]]))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn -main [& args]
  (println "=== Email Watcher ===\n")

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

      ;; Default: show usage
      (do
        (println "Usage:")
        (println "  clojure -M:watch all              - Watch all new emails")
        (println "  clojure -M:watch subject <text>   - Watch for subject containing text")
        (println "  clojure -M:watch from <email>     - Watch for emails from sender")
        (println)
        (println "Examples:")
        (println "  clojure -M:watch all")
        (println "  clojure -M:watch subject \"urgent\"")
        (println "  clojure -M:watch from \"boss@company.com\"")
        (System/exit 1)))))

(comment
  ;; REPL Examples

  ;; 1. Load settings
  (def settings (secrets/require-secret! :email))

  ;; 2. Watch all emails with default callback
  (def watcher (start-watching settings
                               [[{:all true} default-callback]]))
  ;; Stop when done
  (stop-watching watcher)

  ;; 3. Watch with specific rules
  (def watcher
    (start-watching settings
                    [[{:subject "urgent"}
                      #(println "🚨 URGENT:" (:subject %))]

                     [{:from "boss@company.com"}
                      #(println "👔 From boss:" (:subject %))]

                     [{:all true}
                      default-callback]]))

  ;; 4. Custom callback with full message processing
  (def watcher
    (start-watching settings
                    [[{:subject "invoice"}
                      (fn [msg]
                        (println "💰 Invoice received!")
                        (println "From:" (get-in msg [:from :address]))
                        (println "Body:" (read/get-message-body msg)))]]))

  ;; 5. Function-based rule (custom logic)
  (def watcher
    (start-watching settings
                    [[(fn [msg]
                        (and (str/includes? (:subject msg) "report")
                             (> (count (read/get-message-body msg)) 1000)))
                      #(println "📊 Long report received:" (:subject %))]]))

  ;; 6. Watch in background (non-blocking)
  (def watcher-future
    (future
      (start-watching settings
                      [[{:all true} default-callback]])))

  ;; CLI examples
  (-main "all")
  (-main "subject" "urgent")
  (-main "from" "notifications@github.com"))
