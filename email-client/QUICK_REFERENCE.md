# Email Client Quick Reference

## Setup (One-Time)

Add your email configuration to `~/secrets.edn`:

```clojure
{:email
 {:smtp {:host "smtp.gmail.com" :port 587 
         :user "you@gmail.com" :pass "app-password" :tls true}
  :imap {:host "imap.gmail.com" :port 993 
         :user "you@gmail.com" :pass "app-password" :ssl true}
  :defaults {:from "you@gmail.com"}}}
```

**Where to put it:**
- `~/secrets.edn` (recommended - user home)
- `./secrets.edn` (project local)
- `~/secrets.edn.enc` (encrypted)

## Send Emails

### CLI
```bash
clojure -M:send to@example.com "Subject" "Body text here"
```

### REPL
```clojure
(require '[email-client.send :as send])
(def settings (send/load-settings))

;; Plain text
(send/send-simple-email settings "to@example.com" "Hi" "Message")

;; HTML
(send/send-html-email settings "to@example.com" "Rich" "<h1>HTML</h1>")

;; With attachments
(send/send-email-with-attachments settings "to@example.com" "Doc" "See attached"
  [{:type "application/pdf" :content (clojure.java.io/file "file.pdf")}])

;; Full control
(send/send-email settings
  {:to ["person@example.com"]
   :cc ["cc@example.com"]
   :bcc ["bcc@example.com"]
   :subject "Subject"
   :body "Message"})
```

## Read Emails

### CLI
```bash
clojure -M:read list              # Last 10 messages
clojure -M:read list 20           # Last 20 messages
clojure -M:read unread            # Unread messages
clojure -M:read read 5            # Read message #5
clojure -M:read search "urgent"   # Search inbox
clojure -M:read folders           # List folders
```

### REPL
```clojure
(require '[email-client.read :as read])
(def settings (read/load-settings))

;; List messages
(def msgs (read/read-inbox settings {:limit 5}))

;; Unread only
(def unread (read/read-unread settings {:limit 10}))

;; Search
(def results (read/search-inbox settings "project"))
(def results (read/search-inbox settings {:from "boss@company.com"}))

;; Get body
(println (read/get-message-body (first msgs)))
(read/get-html-body (first msgs))

;; Different folder
(read/read-inbox settings {:limit 5 :folder "Sent"})
```

## Run Demo
```bash
clojure -M:demo
```

## Gmail Settings (Most Common)

```clojure
{:email
 {:smtp {:host "smtp.gmail.com" :port 587 
         :user "you@gmail.com" :pass "app-password" :tls true}
  :imap {:host "imap.gmail.com" :port 993 
         :user "you@gmail.com" :pass "app-password" :ssl true}
  :defaults {:from "you@gmail.com"}}}
```

**App Password**: https://myaccount.google.com/apppasswords

## Other Providers

### Outlook
```clojure
{:email
 {:smtp {:host "smtp-mail.outlook.com" :port 587 :tls true}
  :imap {:host "outlook.office365.com" :port 993 :ssl true}
  ;; ... add :user, :pass, :defaults
  }}
```

### Yahoo
```clojure
{:email
 {:smtp {:host "smtp.mail.yahoo.com" :port 587 :tls true}
  :imap {:host "imap.mail.yahoo.com" :port 993 :ssl true}
  ;; ... add :user, :pass, :defaults
  }}
```

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Email not found | Add `:email` key to `~/secrets.edn` |
| Authentication failed | Use app-specific password, not main password |
| Connection timeout | Check firewall, verify ports not blocked |
| Can't read emails | Enable IMAP in provider settings |
| "Less secure apps" error | Enable 2FA and generate app password |

## Security Checklist

- ✅ Store credentials in `~/secrets.edn` (outside project)
- ✅ Use app-specific passwords
- ✅ Consider `~/secrets.edn.enc` for encryption
- ✅ Never commit secrets files

## Quick Test

```bash
# 1. Add :email to ~/secrets.edn (see setup above)

# 2. Test
clojure -M:demo

# 3. Send test email
clojure -M:send yourself@example.com "Test" "It works!"

# 4. Read inbox
clojure -M:read list
```
