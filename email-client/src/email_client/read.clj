(ns email-client.read
  "Email reading functionality using clojure-mail library"
  (:require [clojure.string]
            [clojure.java.io]
            [clojure-mail.core :as mail]
            [clojure-mail.message :as message]
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

;; ============================================================================
;; IMAP Connection
;; ============================================================================

(defn get-imap-session
  "Create JavaMail session for IMAP.
  Exposed for use by watch namespace."
  [settings]
  (let [imap (:imap settings)
        props (doto (java.util.Properties.)
                (.put "mail.store.protocol" "imap")
                (.put "mail.imap.host" (:host imap))
                (.put "mail.imap.port" (str (:port imap)))
                (.put "mail.imap.ssl.enable" (str (:ssl imap)))
                (.put "mail.imaps.ssl.enable" (str (:ssl imap)))
                (.put "mail.imap.starttls.enable" (str (not (:ssl imap)))))]
    (javax.mail.Session/getInstance props)))

(defn get-store
  "Create mail store from settings"
  [settings]
  (let [imap (:imap settings)
        session (get-imap-session settings)
        protocol (if (:ssl imap) "imaps" "imap")
        store (.getStore session protocol)]
    (.connect store (:host imap) (:user imap) (:pass imap))
    store))

;; ============================================================================
;; Email Reading
;; ============================================================================

(defn read-inbox
  "Read messages from inbox folder.

  Parameters:
  - settings: Email settings map
  - opts: Options map:
    - :limit - Maximum number of messages to retrieve (default 10)
    - :folder - Folder name (default \"INBOX\")

  Returns: Vector of message maps"
  [settings & [{:keys [limit folder]
                :or {limit 10 folder "INBOX"}}]]
  (println (str "📬 Reading messages from " folder "..."))
  (let [store (get-store settings)
        messages (mail/all-messages store folder)
        limited (take limit messages)
        parsed (mapv message/read-message limited)]
    (println (str "   Retrieved " (count parsed) " message(s)"))
    parsed))

(defn read-unread
  "Read only unread messages from inbox.

  Parameters:
  - settings: Email settings map
  - opts: Options map:
    - :limit - Maximum number of messages to retrieve (default 10)
    - :folder - Folder name (default \"INBOX\")

  Returns: Vector of unread message maps"
  [settings & [{:keys [limit folder]
                :or {limit 10 folder "INBOX"}}]]
  (println (str "📬 Reading unread messages from " folder "..."))
  (let [store (get-store settings)
        messages (mail/all-messages store folder)
        ;; Filter unread by checking if SEEN flag is not present
        unread (filter #(not (some #{"\\Seen"}
                                   (get-in (message/read-message % :fields [:flags])
                                           [:flags])))
                       messages)
        limited (take limit unread)
        parsed (mapv message/read-message limited)]
    (println (str "   Retrieved " (count parsed) " unread message(s)"))
    parsed))

(defn search-inbox
  "Search inbox for messages matching criteria.

  Parameters:
  - settings: Email settings map
  - query: Search query (string or query map)
    - String: searches subject and body
    - Map examples:
      - {:subject \"project\"}
      - {:from \"john@example.com\"}
      - {:body \"urgent\" :subject \"meeting\"}
  - opts: Options map:
    - :limit - Maximum results (default 20)
    - :folder - Folder name (default \"INBOX\")

  Returns: Vector of matching message maps"
  [settings query & [{:keys [limit folder]
                      :or {limit 20 folder "INBOX"}}]]
  (println (str "🔍 Searching " folder " for: " query))
  (let [store (get-store settings)
        ;; Build search query
        search-query (cond
                       (string? query) [:or [:subject query] [:body query]]
                       (map? query) (vec (apply concat query))
                       :else query)
        results (mail/search-inbox store search-query)
        limited (take limit results)
        parsed (mapv message/read-message limited)]
    (println (str "   Found " (count parsed) " message(s)"))
    parsed))

(defn list-folders
  "List all available folders in the mailbox"
  [settings]
  (println "📁 Listing folders...")
  (let [store (get-store settings)
        folders (mail/folders store)]
    (println "✓ Found" (count folders) "folder(s)")
    folders))

(defn get-message-body
  "Extract plain text body from message map.
  
  Handles both plain and multipart messages."
  [msg]
  (let [body (:body msg)
        _ (println "\n🔍 DEBUG get-message-body:")
        _ (println "   Body type:" (type body))
        _ (println "   Body is string?:" (string? body))
        _ (println "   Body is map?:" (map? body))
        _ (println "   Body is sequential?:" (sequential? body))
        _ (when (sequential? body)
            (println "   Body count:" (count body))
            (println "   First element:" (first body)))]
    (cond
      ;; Plain text body (string)
      (string? body)
      (do
        (println "   → Returning plain text body")
        body)

      ;; Single-part message (map with :content-type and :body)
      (map? body)
      (let [content-type (:content-type body)
            body-text (:body body)
            _ (println "   → Single-part message")
            _ (println "   → Content-type:" content-type)
            _ (println "   → Body field type:" (type body-text))]
        (if (string? body-text)
          (do
            (println "   → Returning body text from map")
            body-text)
          (do
            (println "   → Converting body to string")
            (str body-text))))

      ;; Multipart body - extract text/plain (handles both vector and LazySeq)
      (sequential? body)
      (let [;; Realize LazySeq to vector and handle nested LazySeqs
            body-vec (mapv (fn [part]
                             ;; If part is a LazySeq, realize it and get first element
                             (if (and (sequential? part) (not (map? part)))
                               (first (vec part))
                               part))
                           (vec body))
            _ (println "   → Processing multipart body, parts:" (count body-vec))
            ;; Try to find text/plain part (case-insensitive, handles "TEXT/PLAIN" and "text/plain")
            text-part (first (filter (fn [part]
                                       (when-let [ct (:content-type part)]
                                         (re-find #"(?i)text/plain" (str ct))))
                                     body-vec))
            _ (println "   → Text part found:" (if text-part "YES" "NO"))
            _ (when text-part
                (println "   → Text part content-type:" (:content-type text-part))
                (println "   → Text part :body type:" (type (:body text-part))))
            ;; If no text/plain found, try the first part
            part-to-use (or text-part (first body-vec))
            ;; Extract the :body field from the part
            extracted (if (map? part-to-use)
                        (:body part-to-use)
                        part-to-use)
            _ (println "   → Extracted type:" (type extracted))
            _ (println "   → Extracted preview:" (subs (str extracted) 0 (min 100 (count (str extracted)))))]\
        ;; Ensure we return a string, not a map or LazySeq
        (if (string? extracted)
          (do
            (println "   → Returning extracted string")
            extracted)
          (do
            (println "   → Converting to string")
            (str extracted))))

      ;; Fallback
      :else
      (do
        (println "   → Fallback: converting to string")
        (str body)))))

(defn get-html-body
  "Extract HTML body from message map.
  
  Returns HTML content if available, nil otherwise."
  [msg]
  (let [body (:body msg)]
    (when (vector? body)
      (let [html-part (first (filter #(clojure.string/includes?
                                       (:content-type %) "text/html")
                                     body))]
        (get html-part :body)))))

(defn extract-filename-from-content-type
  "Extract filename from content-type header like 'application/pdf; name=file.pdf'
  Handles both quoted and unquoted filenames:
  - name=\"file name.png\" (quoted, with spaces)
  - name=file.png (unquoted, no spaces)"
  [content-type]
  (when content-type
    (when-let [match (re-find #"name=\"?([^\";\s]+(?:\s+[^\";\s]+)*)\"?" content-type)]
      (second match))))

(defn save-attachments
  "Extract and save attachments from email message to temporary files.
  
  Returns vector of maps with:
  - :filename - Original filename
  - :content-type - MIME type
  - :path - Path to saved temporary file
  - :size - File size in bytes"
  [msg]
  (let [body (:body msg)]
    (when (sequential? body)  ; Changed from vector? to sequential? to handle LazySeq
      (->> body
           ;; Filter for parts that have a filename (attachments)
           ;; Allow any content-type as long as it has a filename parameter
           (filter (fn [part]
                     (let [ct (:content-type part)]
                       (and ct
                            (extract-filename-from-content-type ct)))))
           (map (fn [part]
                  (let [content-type (:content-type part)
                        filename (extract-filename-from-content-type content-type)
                        content (:body part)
                        temp-dir (System/getProperty "java.io.tmpdir")
                        ;; Create unique filename to avoid collisions in temp dir
                        unique-name (str (System/currentTimeMillis) "-" filename)
                        temp-file (java.io.File. temp-dir unique-name)]
                    ;; Write content to temp file
                    (with-open [out (java.io.FileOutputStream. temp-file)]
                      (if (bytes? content)
                        (.write out content)
                        ;; If content is InputStream, copy it
                        (when (instance? java.io.InputStream content)
                          (clojure.java.io/copy content out))))
                    {:filename filename  ; Use original filename, not unique-name
                     :content-type (first (clojure.string/split content-type #";"))  ; Clean content-type
                     :path (.getAbsolutePath temp-file)
                     :size (.length temp-file)})))
           vec))))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn print-message
  "Pretty print a message"
  [msg idx]
  (println (str "\n[" idx "]"))
  (println (str "From: " (or (get-in msg [:from :address])
                             (get-in msg [:from :name])
                             (:from msg))))
  (println (str "To: " (or (clojure.string/join ", "
                                                (map :address (:to msg)))
                           (:to msg))))
  (when (:date-sent msg)
    (println (str "Date: " (:date-sent msg))))
  (println (str "Subject: " (:subject msg)))
  (let [body (get-message-body msg)]
    (when (and body (not (empty? body)))
      (println "---")
      (println (subs body 0 (min 200 (count body))))
      (when (> (count body) 200)
        (println "...")))))

(defn print-message-full
  "Print full message details"
  [msg]
  (println "\n=== Message Details ===")
  (println (str "From: " (or (get-in msg [:from :address])
                             (get-in msg [:from :name])
                             (:from msg))))
  (println (str "To: " (or (clojure.string/join ", "
                                                (map :address (:to msg)))
                           (:to msg))))
  (when (seq (:cc msg))
    (println (str "CC: " (clojure.string/join ", " (map :address (:cc msg))))))
  (when (:date-sent msg)
    (println (str "Date: " (:date-sent msg))))
  (println (str "Subject: " (:subject msg)))
  (println "---")
  (println (get-message-body msg))
  (println "\n=== End Message ==="))

(defn -main [& args]
  (println "=== Email Reader ===\n")

  (let [settings (load-settings)]
    (if-not settings
      (System/exit 1)
      (let [[command & params] args]
        (case command
          "list"
          (let [limit (if (first params)
                        (Integer/parseInt (first params))
                        10)
                messages (read-inbox settings {:limit limit})]
            (doseq [[idx msg] (map-indexed vector messages)]
              (print-message msg (inc idx))))

          "unread"
          (let [limit (if (first params)
                        (Integer/parseInt (first params))
                        10)
                messages (read-unread settings {:limit limit})]
            (doseq [[idx msg] (map-indexed vector messages)]
              (print-message msg (inc idx))))

          "read"
          (if-let [msg-num (first params)]
            ;; Read specific message by index
            (let [idx (dec (Integer/parseInt msg-num))
                  messages (read-inbox settings {:limit (inc idx)})
                  msg (nth messages idx nil)]
              (if msg
                (print-message-full msg)
                (println "Error: Message not found")))
            (println "Error: Message number required"))

          "search"
          (if-let [query (first params)]
            (let [messages (search-inbox settings query)]
              (if (seq messages)
                (doseq [[idx msg] (map-indexed vector messages)]
                  (print-message msg (inc idx)))
                (println "No messages found")))
            (println "Error: Search query required"))

          "folders"
          (let [store (get-store settings)
                folders (mail/folders store)]
            (println "\nAvailable folders:")
            (doseq [folder folders]
              (println "  -" folder)))

          ;; Default: show usage
          (do
            (println "Usage:")
            (println "  clojure -M:read list [limit]        - List inbox messages")
            (println "  clojure -M:read unread [limit]      - List unread messages")
            (println "  clojure -M:read read <number>       - Read specific message")
            (println "  clojure -M:read search <query>      - Search inbox")
            (println "  clojure -M:read folders              - List all folders")
            (println)
            (println "Examples:")
            (println "  clojure -M:read list                 - List last 10 messages")
            (println "  clojure -M:read list 20              - List last 20 messages")
            (println "  clojure -M:read unread               - List unread messages")
            (println "  clojure -M:read read 5               - Read message #5")
            (println "  clojure -M:read search \"project\"     - Search for 'project'")
            (println "  clojure -M:read folders              - Show all folders")
            (System/exit 1)))))))

(comment
  ;; REPL Examples

  ;; 1. Load settings
  (def settings (load-settings))

  ;; 2. List folders
  (def store (get-store settings))
  (mail/folders store)

  ;; 3. Read last 5 messages
  (def messages (read-inbox settings {:limit 5}))

  ;; 4. Read unread messages only
  (def unread (read-unread settings {:limit 10}))

  ;; 5. Get message body
  (def msg (first messages))
  (println (get-message-body msg))

  ;; 6. Get HTML body (if available)
  (get-html-body msg)

  ;; 7. Search for messages
  (def results (search-inbox settings "project"))
  (def results (search-inbox settings {:from "boss@company.com"}))
  (def results (search-inbox settings {:subject "urgent" :body "meeting"}))

  ;; 8. Read from different folder
  (read-inbox settings {:limit 5 :folder "Sent"})

  ;; CLI examples
  (-main "list")
  (-main "list" "20")
  (-main "unread")
  (-main "read" "1")
  (-main "search" "project")
  (-main "folders"))
