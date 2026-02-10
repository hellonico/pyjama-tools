# Greeter Tool

A simple demonstration tool for Pyjama's external tool integration.

## Features

- **greet**: Generate personalized greetings in multiple languages and styles
- **analyze-name**: Analyze a name and return interesting facts

## Usage

### As a standalone tool:

```bash
echo '{"function":"greet","params":{"name":"Alice","language":"english","style":"enthusiastic"}}' | clj -M -m greeter-tool.core
```

### As a Pyjama tool:

```clojure
{:my-agent
 {:tools
  {:greet {:type :clojure-project
           :project-path "~/.pyjama/tools/greeter-tool"
           :namespace "greeter-tool.core"}}
  
  :steps
  {:greet-user
   {:tool :greet
    :args {:function "greet"
           :params {:name "{{user-name}}"
                    :language "english"
                    :style "enthusiastic"}}}}}}
```

## Functions

### greet

Generates a personalized greeting.

**Parameters:**
- `name` (required): Name to greet
- `language` (optional): "english", "french", or "japanese" (default: "english")
- `style` (optional): "formal", "casual", or "enthusiastic" (default: "formal")

**Returns:**
```json
{
  "status": "ok",
  "greeting": "Hello Alice!!! So great to see you!",
  "metadata": {
    "name": "Alice",
    "language": "english",
    "style": "enthusiastic",
    "timestamp": "2026-02-10T07:14:58.123Z"
  }
}
```

### analyze-name

Analyzes a name and returns statistics.

**Parameters:**
- `name` (required): Name to analyze

**Returns:**
```json
{
  "status": "ok",
  "analysis": {
    "length": 5,
    "vowels": 3,
    "consonants": 2,
    "first-letter": "A",
    "last-letter": "e",
    "palindrome?": false
  }
}
```
