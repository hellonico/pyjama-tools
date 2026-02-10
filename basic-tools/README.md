# Basic Tools

External tools for Pyjama agents providing web search, Wikipedia lookup, and movie generation capabilities.

## Tools

### web-search
Search the web using Brave Search API.

**Parameters:**
- `query` - Search query string
- `topk` - Number of results to return (default: 3)

**Returns:**
```clojure
{:status :ok
 :query "search term"
 :results [{:title "..." :url "..." :snippet "..."}]}
```

### wiki-search
Search Wikipedia and retrieve article summaries.

**Parameters:**
- `query` - Wikipedia search query

**Returns:**
```clojure
{:status :ok
 :summary "Article summary..."
 :url "https://en.wikipedia.org/..."}
```

### create-movie
Generate video content from images and audio.

**Parameters:**
- `images` - List of image paths
- `audio` - Audio file path
- `output` - Output video path

**Returns:**
```clojure
{:status :ok
 :file "/path/to/output.mp4"}
```

## Usage in Pyjama Agents

```clojure
{:my-agent
 {:tools
  {:web {:fn pyjama.tools.external/execute-clojure-tool
         :args {:git-url "https://github.com/hellonico/pyjama-tools.git"
                :subdir "basic-tools"
                :namespace "basic-tools.core"}}
   
   :wiki {:fn pyjama.tools.external/execute-clojure-tool
          :args {:git-url "https://github.com/hellonico/pyjama-tools.git"
                 :subdir "basic-tools"
                 :namespace "basic-tools.core"}}}
  
  :steps
  {:search
   {:tool :web
    :args {:function "web-search"
           :params {:query "{{user-query}}" :topk 5}}}}}}
```

## Development

```bash
# Test the tool locally
echo '{:function "web-search" :params {:query "Clojure" :topk 3}}' | clj -M -m basic-tools.core
```

## Dependencies

- Brave Search API key (set in secrets)
- clj-http for HTTP requests
- hickory for HTML parsing

## Configuration

### API Keys

The `web-search` tool requires a **Brave Search API key**. 

**To configure:**

1. Get a free API key from [Brave Search API](https://brave.com/search/api/)
2. Store it using the secrets library:

```bash
# Using Pyjama secrets
clj -M:pyjama secrets set brave-api-key YOUR_API_KEY_HERE

# Or using environment variable
export BRAVE_API_KEY=YOUR_API_KEY_HERE
```

**Note:** The `wiki-search` and `create-movie` tools do NOT require API keys.
