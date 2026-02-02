# Email Client Project Summary

## Overview

Created a comprehensive Clojure email client with both sending and reading capabilities, integrated with the secrets library for secure credential management.

## Project Structure

```
email-client/
├── deps.edn                          # Project dependencies
├── email-settings.edn.template       # Template for email configuration
├── .gitignore                        # Protects credentials
├── README.md                         # Comprehensive documentation
└── src/email_client/
    ├── send.clj                      # Email sending (postal)
    ├── read.clj                      # Email reading (clojure-mail)
    └── demo.clj                      # Combined demonstration
```

## Key Features

### Sending Emails (postal)
- ✉️ Plain text and HTML emails
- 📎 Attachment support
- 👥 Multiple recipients (To, CC, BCC)
- 🔐 SMTP with TLS/SSL
- 📧 Gmail, Outlook, Yahoo, Amazon SES support

### Reading Emails (clojure-mail)
- 📬 IMAP email retrieval
- 🔍 Search functionality (subject, sender, body)
- 📁 Folder navigation
- ✨ Unread message filtering
- 🔒 SSL/TLS support

### Configuration Management
- Secrets library integration (`~/secrets.edn`)
- Encrypted storage support (`~/secrets.edn.enc`)
- Environment variable fallback
- Priority-based configuration loading (local → home → env)

## Libraries Used

1. **postal** (2.0.5) - Email sending via SMTP
   - https://github.com/drewr/postal
   - Simple, functional API for sending emails
   - Supports attachments, HTML, and multiple recipients

2. **clojure-mail** (1.0.8) - Email reading via IMAP
   - https://github.com/owainlewis/clojure-mail
   - Wraps JavaMail with Clojure-friendly API
   - Search, filter, and parse email messages

3. **secrets** (local) - Credential management
   - Plugin-based architecture
   - Encrypted storage support
   - Multiple source priority

## Quick Start

### Setup

Add your email configuration to `~/secrets.edn`:

```clojure
{:email
 {:smtp {:host "smtp.gmail.com"
         :port 587
         :user "your-email@gmail.com"
         :pass "your-app-password"  ; Use app-specific password
         :tls true}
  :imap {:host "imap.gmail.com"
         :port 993
         :user "your-email@gmail.com"
         :pass "your-app-password"
         :ssl true}
  :defaults {:from "your-email@gmail.com"}}}
```

**Configuration sources** (priority order):
1. `./secrets.edn` (project local)
2. `./secrets.edn.enc` (project encrypted)
3. `~/secrets.edn` (user home) ⭐ **recommended**
4. `~/secrets.edn.enc` (user home encrypted)
5. Environment variables (`EMAIL__SMTP__HOST`, etc.)
```

### Run Demo

```bash
clojure -M:demo
```

### Send Email

```bash
clojure -M:send recipient@example.com "Subject" "Body text"
```

### Read Email

```bash
clojure -M:read list           # List last 10 messages
clojure -M:read unread         # List unread messages
clojure -M:read search "query" # Search inbox
clojure -M:read folders        # List folders
```

## Gmail Configuration

For Gmail users (most common):

1. **Enable 2FA** on your Google account
2. **Generate app password**:
   - Visit: https://myaccount.google.com/apppasswords
   - Select "Mail" and your device
   - Copy the 16-character password
3. **Use these settings**:
   - SMTP: `smtp.gmail.com:587` with TLS
   - IMAP: `imap.gmail.com:993` with SSL

## Security Best Practices

1. ✅ Add `email-settings.edn` to `.gitignore` (done)
2. ✅ Use app-specific passwords, not main account password
3. ✅ Consider using encrypted secrets (`~/secrets.edn.enc`)
4. ✅ Never commit credentials to version control
5. ✅ Store passphrases in password manager

## Code Examples

### Sending Email (REPL)

```clojure
(require '[email-client.send :as send])

(def settings (send/load-settings))

;; Simple email
(send/send-simple-email settings
                        "recipient@example.com"
                        "Hello"
                        "This is a test")

;; HTML email
(send/send-html-email settings
                      "recipient@example.com"
                      "Rich Content"
                      "<h1>Hello!</h1><p>HTML content</p>")

;; With attachments
(send/send-email-with-attachments 
  settings
  "recipient@example.com"
  "Document"
  "See attached"
  [{:type "application/pdf"
    :content (clojure.java.io/file "doc.pdf")}])
```

### Reading Email (REPL)

```clojure
(require '[email-client.read :as read])

(def settings (read/load-settings))

;; Read recent messages
(def messages (read/read-inbox settings {:limit 5}))

;; Read unread only
(def unread (read/read-unread settings {:limit 10}))

;; Search inbox
(def results (read/search-inbox settings "urgent"))

;; Search with criteria
(def results (read/search-inbox settings 
                                {:from "boss@company.com"
                                 :subject "meeting"}))

;; Get message body
(println (read/get-message-body (first messages)))
```

## Secrets Integration

The email client uses the secrets library's simple `require-secret!` function for configuration:

```clojure
(defn load-settings []
  (secrets/require-secret! :email))
```

The secrets library automatically checks for credentials in this order:

1. `./secrets.edn` (project local)
2. `./secrets.edn.enc` (project encrypted)
3. `~/secrets.edn` (user home) ⭐ **recommended**
4. `~/secrets.edn.enc` (user home encrypted)
5. Environment variables (`EMAIL__*` prefix)

Example `~/secrets.edn`:
```clojure
{:email 
 {:smtp {:host "smtp.gmail.com"
         :port 587
         :user "your@gmail.com"
         :pass "app-password"
         :tls true}
  :imap {:host "imap.gmail.com"
         :port 993
         :user "your@gmail.com"
         :pass "app-password"
         :ssl true}
  :defaults {:from "your@gmail.com"}}}
```

## Related Work

### Secrets Library Example

Created `retrieve_secret_map.clj` in the secrets codebase demonstrating:
- Getting all secrets as a map
- Retrieving nested secret maps
- Using `require-secret!` for mandatory configs
- Reloading secrets dynamically

Location: `/secrets/src/secrets/examples/retrieve_secret_map.clj`

## Testing

```bash
# Test secrets example
cd /path/to/secrets
clojure -M -m secrets.examples.retrieve-secret-map

# Test email demo (requires configuration)
cd /path/to/email-client
clojure -M:demo
```

## Next Steps

To use this email client:

1. ✅ Configuration uses secrets library
2. ⏳ Add `:email` to your `~/secrets.edn`
3. ⏳ Test with demo: `clojure -M:demo`
4. ⏳ Send test email: `clojure -M:send your@email.com "Test" "Hello"`
5. ⏳ Read your inbox: `clojure -M:read list`

## Files Created

In **secrets** codebase:
- `src/secrets/examples/retrieve_secret_map.clj` - Secret map retrieval example

In **pyjama-agent-showcases/email-client**:
- `deps.edn` - Project dependencies
- `email-settings.edn.template` - Configuration template
- `.gitignore` - Security protection
- `README.md` - Full documentation
- `src/email_client/send.clj` - Sending functionality
- `src/email_client/read.clj` - Reading functionality
- `src/email_client/demo.clj` - Combined demo

## Documentation

See `README.md` for:
- Complete setup guide
- API reference
- Email provider configurations
- Troubleshooting guide
- Security best practices
- Usage examples
