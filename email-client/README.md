# Email Client

A Clojure email client for sending and reading emails using [postal](https://github.com/drewr/postal) and [clojure-mail](https://github.com/owainlewis/clojure-mail).

## Features

### Sending (via postal)
- ✉️ Send plain text and HTML emails
- 📎 Support for attachments
- 👥 Multiple recipients (To, CC, BCC)
- 🔐 SMTP with TLS/SSL support
- 📧 Gmail, Outlook, Yahoo, Amazon SES support

### Reading (via clojure-mail)
- 📬 Read emails via IMAP
- 🔍 Search inbox by subject, sender, body
- 📁 List and navigate folders
- ✨ Filter unread messages
- 🔒 SSL/TLS support

### Configuration
- 📝 Local settings file (`email-settings.edn`)
- 🔐 Secrets integration (via `~/secrets.edn`)
- 🌍 Environment variable support

## Installation

This project uses the secrets library for configuration management:

```bash
cd email-client
```

## Configuration

The email client uses the [secrets library](../../../secrets) for configuration management. Add your email settings to `secrets.edn`:

### Setup

Add to `~/secrets.edn` (recommended) or `./secrets.edn` (project local):

```clojure
{:email
 {:smtp {:host "smtp.gmail.com"
         :port 587
         :user "your-email@gmail.com"
         :pass "your-app-password"
         :tls true}
  
  :imap {:host "imap.gmail.com"
         :port 993
         :user "your-email@gmail.com"
         :pass "your-app-password"
         :ssl true}
  
  :defaults {:from "your-email@gmail.com"}}}
```

**Configuration sources** (checked in priority order):
1. `./secrets.edn` (project local)
2. `./secrets.edn.enc` (project encrypted)
3. `~/secrets.edn` (user home) ⭐ **recommended**
4. `~/secrets.edn.enc` (user home encrypted)
5. Environment variables (`EMAIL__SMTP__HOST`, etc.)

### Gmail Setup

1. Enable 2-factor authentication on your Google account
2. Generate an app-specific password:
   - Visit: https://myaccount.google.com/apppasswords
   - Select "Mail" and your device
   - Use the generated 16-character password
3. Use these settings:
   - SMTP: `smtp.gmail.com:587` with TLS
   - IMAP: `imap.gmail.com:993` with SSL

### Encrypted Secrets (Optional)

For added security, use encrypted secrets:

```bash
# In your secrets directory
clojure -M -m secrets.core/write-encrypted-secrets! \
  ~/secrets.edn.enc \
  '{:email {:smtp {...} :imap {...}}}'
```

Set the passphrase:
```bash
export SECRETS_PASSPHRASE="your-secure-passphrase"
```

## Usage

### Run Demo

```bash
clojure -M:demo
```

### Send Emails

```bash
# Send a simple email
clojure -M:send recipient@example.com "Subject" "Email body text"

# Send to multiple recipients
clojure -M:send "person1@example.com,person2@example.com" "Subject" "Body"
```

From the REPL:
```clojure
(require '[email-client.send :as send])

(def settings (send/load-settings))

;; Simple email
(send/send-simple-email settings
                        "recipient@example.com"
                        "Hello"
                        "This is a test message")

;; HTML email
(send/send-html-email settings
                      "recipient@example.com"
                      "HTML Email"
                      "<h1>Hello!</h1><p>This is <strong>HTML</strong>.</p>")

;; With attachments
(send/send-email-with-attachments settings
                                  "recipient@example.com"
                                  "Document"
                                  "See attached."
                                  [{:type "application/pdf"
                                    :content (clojure.java.io/file "doc.pdf")}])

;; Full control
(send/send-email settings
                 {:to ["primary@example.com"]
                  :cc ["cc@example.com"]
                  :bcc ["bcc@example.com"]
                  :subject "Full Control"
                  :body "Complete control over email"})
```

### Read Emails

```bash
# List last 10 messages
clojure -M:read list

# List last 20 messages
clojure -M:read list 20

# List unread messages
clojure -M:read unread

# Read specific message
clojure -M:read read 5

# Search inbox
clojure -M:read search "project"

# List folders
clojure -M:read folders
```

From the REPL:
```clojure
(require '[email-client.read :as read])

(def settings (read/load-settings))

;; Read last 5 messages
(def messages (read/read-inbox settings {:limit 5}))

;; Read unread only
(def unread (read/read-unread settings {:limit 10}))

;; Search
(def results (read/search-inbox settings "urgent"))
(def results (read/search-inbox settings {:from "boss@company.com"}))
(def results (read/search-inbox settings {:subject "meeting" :body "tomorrow"}))

;; Get message body
(def msg (first messages))
(println (read/get-message-body msg))

;; Get HTML body
(read/get-html-body msg)

;; List folders
(def store (read/get-store settings))
(clojure-mail.core/folders store)
```

## Email Providers

### Gmail
```clojure
{:smtp {:host "smtp.gmail.com" :port 587 :tls true}
 :imap {:host "imap.gmail.com" :port 993 :ssl true}}
```

### Outlook/Office 365
```clojure
{:smtp {:host "smtp-mail.outlook.com" :port 587 :tls true}
 :imap {:host "outlook.office365.com" :port 993 :ssl true}}
```

### Yahoo Mail
```clojure
{:smtp {:host "smtp.mail.yahoo.com" :port 587 :tls true}
 :imap {:host "imap.mail.yahoo.com" :port 993 :ssl true}}
```

### Amazon SES
```clojure
{:smtp {:host "email-smtp.us-east-1.amazonaws.com"
        :port 587
        :user "AKIA..."  ; Your SMTP IAM credentials
        :pass "Your-SMTP-Password"}}
```

## API Reference

### Send Module (`email-client.send`)

- `(load-settings)` - Load email settings from file or secrets
- `(send-email settings message)` - Send email with full control
- `(send-simple-email settings to subject body)` - Send plain text email
- `(send-html-email settings to subject html-body)` - Send HTML email
- `(send-email-with-attachments settings to subject body attachments)` - Send with attachments

### Read Module (`email-client.read`)

- `(load-settings)` - Load email settings
- `(get-store settings)` - Create IMAP store connection
- `(read-inbox settings opts)` - Read inbox messages
- `(read-unread settings opts)` - Read unread messages only
- `(search-inbox settings query opts)` - Search inbox
- `(get-message-body msg)` - Extract plain text body
- `(get-html-body msg)` - Extract HTML body

## Security

⚠️ **Important Security Notes:**

1. **Use secrets library** - Store credentials in `~/secrets.edn` (outside project)
2. **Use app-specific passwords** - Don't use your main account password
3. **Use encrypted secrets** - Consider using `~/secrets.edn.enc` with encryption
4. **Protect passphrases** - Store `SECRETS_PASSPHRASE` in password manager
5. **Never commit secrets** - The secrets library handles this automatically

## Troubleshooting

### "Email not found" Error
- Add `:email` key to your `~/secrets.edn` or `./secrets.edn`
- See the configuration section above for the correct format
- Check that the file exists and has valid EDN syntax

### Gmail "Less Secure Apps" Error
- Enable 2FA and use an app-specific password
- Visit: https://myaccount.google.com/apppasswords

### Connection Timeout
- Check firewall settings
- Verify SMTP/IMAP ports are not blocked
- Confirm server addresses are correct

### Authentication Failed
- Double-check username and password
- Ensure TLS/SSL settings match provider requirements
- Try regenerating app-specific password

### Can't Read Emails
- Verify IMAP is enabled in your email provider settings
- Check folder names (some providers use different names)
- Ensure SSL/TLS settings are correct

## Examples

See the `comment` blocks in each namespace for more examples:
- `src/email_client/send.clj` - Sending examples
- `src/email_client/read.clj` - Reading examples
- `src/email_client/demo.clj` - Complete demo

## License

Copyright © 2026

## Related Projects

- [postal](https://github.com/drewr/postal) - Clojure email sending
- [clojure-mail](https://github.com/owainlewis/clojure-mail) - Clojure IMAP email reading
- [secrets](../../../secrets) - Secrets management library
