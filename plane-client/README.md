# Plane API Client (Clojure)

A Clojure client library for the [Plane](https://plane.so/) project management API, with special focus on email integration and attachment management.

## Features

- ✅ **Projects**: List, create, and manage projects
- ✅ **Work Items (Issues)**: Full CRUD operations with filtering
- ✅ **Attachments**: Upload, download, and list attachments (3-step S3 presigned flow)
- ✅ **Email Integration**: Analyze and import emails as work items with intelligent priority detection
- ✅ **Export/Import**: Backup and restore project data
- ✅ **Filtering**: Advanced filtering with Plane's filter DSL

## Installation

### Prerequisites

- Clojure 1.11.1+
- Access to a Plane instance (cloud or self-hosted)
- API key from your Plane instance

### Setup

1. Clone this repository:
```bash
git clone <repo-url>
cd plane-client
```

2. Create `secrets.edn` with your Plane credentials:
```clojure
{:base-url "https://your-plane-instance.com"
 :api-key "plane_api_xxxxxxxxxxxxx"
 :workspace-slug "your-workspace"}
```

3. Test the connection:
```bash
clojure -M:projects
```

## Quick Start

### List Projects
```clojure
(require '[plane-client.core :as plane]
         '[plane-client.projects :as projects])

(def settings (plane/load-settings))
(def my-projects (projects/list-projects settings))
```

### Work with Issues
```clojure
(require '[plane-client.work-items :as items])

;; List all work items in a project
(def work-items (items/list-work-items settings project-id))

;; Create a new work item
(items/create-work-item settings project-id
  {:name "Fix login bug"
   :priority "urgent"
   :description "Users cannot log in on mobile"})

;; Update a work item
(items/update-work-item settings project-id work-item-id
  {:state state-id
   :priority "high"})
```

### Attachment Operations

#### Upload an Attachment
```clojure
(require '[plane-client.attachments :as att])

;; Upload a file to a work item
(att/upload-attachment settings project-id work-item-id "./screenshot.png")
```

#### Download Attachments
```clojure
;; List all attachments
(def attachments (att/list-attachments settings project-id work-item-id))

;; Download a specific attachment
(att/download-attachment settings project-id work-item-id 
                        (first attachments) 
                        "./download.png")

;; Download all attachments to a directory
(att/download-all-attachments settings project-id work-item-id "./downloads")
```

### Email Integration
```clojure
(require '[plane-client.email :as email])

;; Analyze an email and create a work item
(email/email->work-item settings project-id email-data)

;; The library automatically:
;; - Detects priority from keywords (URGENT, CRITICAL, etc.)
;; - Formats the email as a work item
;; - Preserves metadata (from, to, date)
```

## Command-Line Usage

The project includes several convenient CLI commands:

### Project Management
```bash
# List all projects
clojure -M:projects

# List work items
clojure -M:work-items
```

### Demos and Tests
```bash
# Test attachment upload/download
clojure -M:test-attachment
clojure -M:test-upload

# Email integration demo
clojure -M:email-demo

# Export project data
clojure -M:export-demo

# Filter demo
clojure -M:filter-demo

# Basic functionality demo
clojure -M:demo
```

## Architecture

### API Endpoint Discovery

This client supports both `/api/v1/` and legacy `/api/` endpoints with automatic fallback. 

Key findings from API investigation:
- **Attachments**: Use `/api/v1/.../issue-attachments/` (supports API key auth)
- **Upload Flow**: 3-step S3 presigned POST
  1. POST to get upload credentials
  2. POST multipart form to S3
  3. PATCH to finalize attachment
- **Download**: Individual attachment endpoint returns 302 redirect to signed URL

### Filtering

The client supports Plane's advanced filtering using the filter DSL:

```clojure
(require '[plane-client.filter :as filter])

;; Create filters programmatically
(def my-filter
  {:priority {\"$in\" [\"urgent\" \"high\"]}
   :state {\"$eq\" state-id}})

;; Apply filters when listing work items
(items/list-work-items settings project-id :filters my-filter)
```

## Email Integration Details

The email module provides intelligent email-to-work-item conversion:

### Priority Detection
Automatically detects priority from email content:
- **URGENT**: Keywords like "URGENT", "CRITICAL", "ASAP", "EMERGENCY"
- **HIGH**: "Important", "High Priority", "Needs Attention"  
- **MEDIUM**: "FYI", "For your information", "Please review"
- **LOW**: "When you can", "No rush", "Low priority"

### Email Metadata
Preserves important email metadata:
```clojure
{:name "Email subject line"
 :description "Formatted email body with metadata"
 :priority "urgent"  ; Auto-detected
 :labels ["email" "customer-inquiry"]}
```

## Development

### Project Structure
```
plane-client/
├── src/plane_client/
│   ├── core.clj           # Core API functions
│   ├── projects.clj       # Project operations
│   ├── work_items.clj     # Issue CRUD
│   ├── attachments.clj    # Attachment upload/download
│   ├── email.clj          # Email integration
│   ├── filter.clj         # Filter DSL
│   └── export.clj         # Data export/import
├── test/plane_client/demo/  # Test scripts
│   ├── basic.clj
│   ├── test_attachment.clj
│   └── email_integration.clj
├── deps.edn               # Dependencies
└── README.md
```

### Running Tests
```bash
# Run a specific test/demo
clojure -M:test-attachment

# Run the REPL for interactive development
clojure -M:repl
```

## API Reference

### Core Functions (`plane-client.core`)
- `load-settings` - Load configuration from secrets.edn
- `request` - Make authenticated API request
- `get-api-key` - Retrieve API key from settings

### Projects (`plane-client.projects`)
- `list-projects` - List all accessible projects
- `create-project` - Create a new project
- `get-project` - Get project details

### Work Items (`plane-client.work-items`)
- `list-work-items` - List issues with optional filters
- `get-work-item` - Get single issue details
- `create-work-item` - Create new issue
- `update-work-item` - Update existing issue
- `delete-work-item` - Delete an issue

### Attachments (`plane-client.attachments`)
- `list-attachments` - List all attachments for a work item
- `download-attachment` - Download a single attachment
- `download-all-attachments` - Download all attachments to directory
- `upload-attachment` - Upload a file as attachment (3-step S3 flow)

### Email (`plane-client.email`)
- `email->work-item` - Convert email to work item
- `detect-priority` - Detect priority from email content
- `format-email-description` - Format email as issue description

## Troubleshooting

### Authentication Issues
- Ensure your API key is valid and not expired
- Check that the workspace slug matches your workspace
- Verify the base-url is correct (include http:// or https://)

### Attachment Upload Fails
- The upload uses a 3-step S3 presigned POST flow
- Step 1 (credentials) requires API key authentication
- Step 2 (S3 upload) uses pre-signed form fields
- Step 3 (finalize) expects 200 or 204 response

### Endpoint Not Found (404)
- This client supports multiple API versions with automatic fallback
- Check your Plane instance version
- Some endpoints may vary between cloud and self-hosted instances

## License

MIT License - See LICENSE file for details

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Submit a pull request

## Acknowledgments

- Built for [Plane](https://plane.so/) project management
- Uses [clj-http](https://github.com/dakrone/clj-http) for HTTP requests
- Email parsing with [Cheshire](https://github.com/dakrone/cheshire) JSON

---

**Note**: This client was developed through extensive API endpoint discovery and testing against a self-hosted Plane instance. API endpoints may vary between Plane versions.
