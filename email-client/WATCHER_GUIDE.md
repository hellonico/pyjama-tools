# Email Watcher - Server Mode

## Overview

The email watcher provides a server-like mode that continuously monitors your IMAP inbox for new emails and triggers callbacks based on matching rules.

## Quick Start

### Watch All Emails

```bash
clojure -M:watch all
```

This will print subject and preview of all new emails as they arrive.

### Watch Specific Emails

```bash
# Watch for subject containing "urgent"
clojure -M:watch subject "urgent"

# Watch for emails from specific sender
clojure -M:watch from "boss@company.com"
```

## Rule-Based Matching

### Rule Types

Rules can match on various message attributes:

```clojure
;; Subject contains pattern (case-insensitive)
{:subject "urgent"}

;; From address contains pattern
{:from "boss@company.com"}

;; To address contains pattern
{:to "notifications"}

;; Body contains pattern
{:body "invoice"}

;; Match all messages
{:all true}

;; Custom function
(fn [msg]
  (and (str/includes? (:subject msg) "report")
       (> (count (read/get-message-body msg)) 1000)))
```

### Multiple Conditions

Multiple conditions in a single rule are AND'd together:

```clojure
{:subject "urgent" :from "boss@"}
;; Matches emails from boss with "urgent" in subject
```

## REPL Usage

### Basic Watching

```clojure
(require '[email-client.watch :as watch]
         '[secrets.core :as secrets])

(def settings (secrets/require-secret! :email))

;; Watch all emails
(def watcher (watch/start-watching settings
                                   [[{:all true} watch/default-callback]]))

;; Stop watching
(watch/stop-watching watcher)
```

### Custom Callbacks

```clojure
;; Print urgent emails differently
(def watcher
  (watch/start-watching settings
    [[{:subject "urgent"}
      (fn [msg]
        (println "🚨 URGENT EMAIL!")
        (println "From:" (get-in msg [:from :address]))
        (println "Subject:" (:subject msg)))]]))

;; Process invoices
(def watcher
  (watch/start-watching settings
    [[{:subject "invoice"}
      (fn [msg]
        (println "💰 Invoice received!")
        (let [body (read/get-message-body msg)]
          ;; Parse invoice, save to database, etc.
          (process-invoice body)))]]))

;; Multiple rules
(def watcher
  (watch/start-watching settings
    [[{:subject "urgent"} handle-urgent]
     [{:from "boss@company.com"} handle-boss-email]
     [{:subject "report" :body "quarterly"} handle-quarterly-report]
     [{:all true} watch/default-callback]]))
```

### Background Watching

```clojure
;; Run in background thread
(def watcher-future
  (future
    (watch/start-watching settings
                         [[{:all true} watch/default-callback]])))

;; Do other work...

;; Stop background watcher
(future-cancel watcher-future)
```

## How It Works

The watcher uses IMAP IDLE to receive real-time notifications when new messages arrive:

1. **Connect** - Establishes IMAP connection
2. **Listen** - Uses IDLE to wait for server notifications
3. **Match** - Tests each new message against rules
4. **Callback** - Calls functions for matching rules
5. **Repeat** - Continues listening until stopped

This is more efficient than polling, as the server pushes notifications when messages arrive.

## Message Object

The message object passed to callbacks has this structure:

```clojure
{:subject "Email Subject"
 :from {:address "sender@example.com" :name "Sender Name"}
 :to [{:address "you@example.com"}]
 :cc [...]
 :date-sent #inst "2026-02-02"
 :body "Message body or multipart structure"
 :flags ["\\Seen" ...]
 ;; ... more fields
}
```

Use `read/get-message-body` to extract plain text from complex multipart messages.

## Use Cases

### Email-Based Automation

```clojure
;; Auto-respond to support emails
(watch/start-watching settings
  [[{:to "support@company.com"}
    (fn [msg]
      (send-auto-reply msg)
      (create-support-ticket msg))]])
```

### Monitoring & Alerts

```clojure
;; Alert on critical emails
(watch/start-watching settings
  [[{:subject "production" :subject "down"}
    (fn [msg]
      (send-sms-alert)
      (trigger-pagerduty)
      (log-incident msg))]])
```

### Email Processing Pipeline

```clojure
;; Process different email types
(watch/start-watching settings
  [[{:subject "invoice"} process-invoice]
   [{:subject "order"} process-order]
   [{:subject "receipt"} archive-receipt]
   [{:from "automated@"} handle-notification]])
```

## Error Handling

Add error handlers to the watcher:

```clojure
(watch/start-watching settings rules
  {:on-error (fn [e]
               (println "Error:" (.getMessage e))
               (log-error e))
   :on-start #(println "Watcher started successfully")})
```

## Tips

1. **Order matters** - Rules are processed in order, first match wins for each callback
2. **Multiple matches** - A message can match multiple rules and trigger multiple callbacks
3. **Keep callbacks fast** - Long-running callbacks block processing of other messages
4. **Use futures for slow work** - Offload heavy processing to background threads
5. **Handle errors** - Always wrap callbacks in try-catch to prevent watcher crashes

## Stopping the Watcher

### CLI (Ctrl+C)

When running from CLI, press Ctrl+C to gracefully stop the watcher.

### REPL

```clojure
(watch/stop-watching watcher)
```

### Automatic Cleanup

The watcher registers a shutdown hook to clean up resources when the JVM exits.

## Performance

- **IDLE mode** is very efficient - uses minimal resources when idle
- **Callbacks run sequentially** - Design for quick processing
- **Background threads** - Use `future` for CPU-intensive work in callbacks

## Troubleshooting

### "Connection lost"
- Check internet connection
- Server may have timeout limits - watcher will try to reconnect

### "Folder not found"
- Verify folder name exists (case-sensitive)
- Use `(read/list-folders settings)` to see available folders

### Callbacks not triggering
- Test rule matching: `(watch/match-rule? rule msg)`
- Check message structure: `(println msg)`
- Verify callback function signature: `(fn [msg] ...)`

## Example: Complete Email Bot

```clojure
(ns my-bot
  (:require [email-client.watch :as watch]
            [email-client.send :as send]
            [email-client.read :as read]
            [secrets.core :as secrets]))

(defn auto-reply [msg]
  (when-not (str/includes? (:subject msg) "Re:")
    (send/send-simple-email 
      settings
      (get-in msg [:from :address])
      (str "Re: " (:subject msg))
      "Thank you for your email. We'll respond within 24 hours.")))

(defn -main []
  (let [settings (secrets/require-secret! :email)]
    (watch/watch-with-rules settings
      [[{:to "support@mycompany.com"} auto-reply]
       [{:subject "unsubscribe"} handle-unsubscribe]
       [{:all true} log-all-emails]])))
```

Run it:
```bash
clojure -M -m my-bot
```
