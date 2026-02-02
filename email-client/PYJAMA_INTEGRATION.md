# Email Tools for Pyjama Framework

This document explains how email tools integrate with the Pyjama agent framework.

## Overview

The email client provides three tools that can be used by Pyjama agents:

1. **send-email** - Send emails
2. **read-emails** - Read emails from inbox
3. **watch-emails** - Watch for new emails (streaming)

## Tool Registration

Tools are defined in `/email-client/src/email_client/tools/registry.clj`:

```clojure
(def tools
  {:send-email {...}
   :read-emails {...}
   :watch-emails {...}})
```

These tools follow the Pyjama tool specification:
- `:name` - Tool identifier
- `:description` - What the tool does
- `:parameters` - JSON Schema for parameters
- `:function` - Implementation (fn [args context] ...)

## Context Handling

All tools receive a `context` map from Pyjama:

```clojure
{:secrets {:email {...}}        ; Auto-loaded from secrets.edn
 :stream-callback (fn [data] ...)} ; For streaming tools
```

**Secrets are loaded automatically!** Agents just declare:

```edn
{:secrets [:email]}
```

And the framework loads `:email` from `secrets.edn` and passes it to tools.

## Agent Definition (EDN)

Agents are defined declaratively in EDN files:

```edn
{:name "email-assistant"
 :description "Email management agent"
 
 :llm {:provider :gemini
       :model "gemini-2.0-flash-exp"}
 
 :secrets [:email]              ; Auto-loaded!
 :tools [:send-email :read-emails]
 
 :system-prompt "You are an email assistant..."
 
 :examples [...]}
```

## Usage Pattern

### From Agent's Perspective (EDN)

The agent just declares tools and secrets:

```edn
{:tools [:send-email :read-emails]
 :secrets [:email]}
```

### Framework's Responsibility

Pyjama framework:
1. Loads `secrets.edn`
2. Extracts `:email` section
3. Calls tool functions with context:
   ```clojure
   (send-email {:to "..." :subject "..." :body "..."}
               {:secrets {:email {...}}})
   ```

### Tool's Responsibility

Tools extract what they need from context:

```clojure
(defn send-email [args context]
  (let [settings (get-in context [:secrets :email])]
    (send/send-simple-email settings (:to args) ...)))
```

## Complete Flow

```
1. Agent Definition (agent.edn)
   ↓
   {:tools [:send-email]
    :secrets [:email]}

2. Pyjama Loads
   ↓
   - Reads agent.edn
   - Loads secrets.edn
   - Registers tools

3. LLM Decides to Use Tool
   ↓
   {tool: "send-email"
    args: {to: "...", subject: "...", body: "..."}}

4. Framework Calls Tool
   ↓
   (send-email args {:secrets {:email {...}}})

5. Tool Executes
   ↓
   Uses settings to send email via SMTP

6. Result Returns to LLM
   ↓
   {:success true, :message "Email sent"}
```

## File Structure

```
email-client/
└── src/email_client/
    ├── send.clj              # SMTP sending
    ├── read.clj              # IMAP reading
    ├── watch-poll.clj        # Email monitoring
    └── tools/
        ├── registry.clj      # Pyjama tool definitions
        └── email.clj         # (old standalone version)

pyjama-agent-showcases/
└── email-watcher-agent/
    ├── agent.edn             # Full watcher agent
    ├── simple-agent.edn      # Simple checker
    ├── deps.edn              # Dependencies
    ├── README.md             # Documentation
    └── src/
        └── email_watcher_agent/
            └── example.clj   # Usage examples
```

## Key Benefits

✅ **Declarative** - Agents are data, not code
✅ **Automatic** - Secrets loaded by framework
✅ **Isolated** - Tools receive clean context
✅ **Reusable** - Same tools for all email agents
✅ **Type-safe** - JSON Schema validation
✅ **Streaming** - Support for continuous monitoring

## Example Agents

### Simple Email Checker
```bash
pyjama run simple-agent.edn
```

User: "Check my email"
Agent: Uses `read-emails` → Summarizes results

### Continuous Watcher
```bash
pyjama run agent.edn
```

Agent: Uses `watch-emails` → Streams new emails → Processes each

### Custom Auto-Responder
```edn
{:name "auto-responder"
 :tools [:watch-emails :send-email]
 :secrets [:email]
 :prompt "Watch for emails from customers.
          Reply with: 'Thank you, we will respond within 24 hours.'"}
```

## Next Steps

1. ✅ Tools defined in `registry.clj`
2. ✅ Agents defined in `*.edn` files
3. ⏳ Register tools with Pyjama on startup
4. ⏳ Test agents with Pyjama CLI
5. ⏳ Deploy production email agents

## Integration with Pyjama

To make these tools available in Pyjama:

```clojure
;; In Pyjama's startup code
(require '[email-client.tools.registry :as email-tools])

(defn register-email-tools! []
  (doseq [[name tool-def] email-tools/tools]
    (register-tool! name tool-def)))
```

Then agents can use them by declaring:

```edn
{:tools [:send-email :read-emails :watch-emails]}
```
