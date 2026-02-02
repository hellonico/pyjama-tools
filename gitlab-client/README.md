# GitLab Client

A Clojure client for the GitLab API, focused on merge request automation and code review workflows.

## Features

- ✅ List assigned merge requests
- ✅ Get merge request details
- ✅ Fetch merge request diffs/changes
- ✅ Add comments to merge requests
- ✅ Support for both GitLab.com and self-hosted instances

## Configuration

Add your GitLab credentials to `secrets.edn`:

```clojure
{:gitlab-token "glpat-xxxxxxxxxxxxxxxxxxxx"
 :gitlab-url "https://gitlab.com"}  ; Optional, defaults to gitlab.com
```

Or set environment variables:
```bash
export GITLAB_TOKEN="glpat-xxxxxxxxxxxxxxxxxxxx"
export GITLAB_URL="https://gitlab.example.com"  # Optional
```

### Getting a GitLab Token

1. Go to GitLab → Settings → Access Tokens
2. Create a new token with scopes: `api`, `read_api`, `read_repository`
3. Copy the token and add it to your secrets

## Usage

### List Assigned Merge Requests

```clojure
(require '[gitlab-client.core :as gitlab]
         '[gitlab-client.merge-requests :as mrs])

(def settings (gitlab/load-settings))

;; List all open MRs assigned to you
(def my-mrs (mrs/list-assigned-merge-requests settings))

;; List with options
(def all-mrs (mrs/list-assigned-merge-requests settings 
                {:state "all"        ; opened, closed, merged, all
                 :scope "assigned_to_me"  ; assigned_to_me, created_by_me, all
                 :per-page 50}))

;; Pretty print
(mrs/print-merge-requests my-mrs)
```

### Get Merge Request Details

```clojure
;; Get specific MR by project and IID
(def mr (mrs/get-merge-request settings "group/project" 123))

;; Print details
(mrs/print-merge-request mr)
```

### Get Merge Request Diff

```clojure
;; Get changes (diff)
(def changes (mrs/get-merge-request-changes settings "group/project" 123))

;; Extract unified diff content
(def diff-content (mrs/extract-diff-content changes))
(println diff-content)

;; Get detailed diffs
(def diffs (mrs/get-merge-request-diffs settings "group/project" 123))
```

### Add Comment to Merge Request

```clojure
;; Add a comment (supports markdown)
(mrs/add-merge-request-note settings 
                            "group/project" 
                            123 
                            "## Code Review\n\nLGTM! 🚀")
```

## API Reference

### Core Functions

- `(load-settings)` - Load GitLab settings from secrets/env
- `(get-current-user settings)` - Get authenticated user info

### Merge Request Functions

- `(list-assigned-merge-requests settings opts)` - List MRs assigned to you
- `(get-merge-request settings project-id mr-iid)` - Get MR details
- `(get-merge-request-changes settings project-id mr-iid)` - Get MR changes/diff
- `(get-merge-request-diffs settings project-id mr-iid)` - Get detailed diffs
- `(add-merge-request-note settings project-id mr-iid body)` - Add comment
- `(extract-diff-content changes)` - Extract unified diff from changes
- `(print-merge-request mr)` - Pretty-print MR
- `(print-merge-requests mrs)` - Pretty-print MR list

## Integration with Codebase Analyzer

This client is designed to work with the codebase-analyzer for automated code reviews:

```clojure
;; 1. Fetch assigned MRs
(def mrs (mrs/list-assigned-merge-requests settings))

;; 2. Get diff for each MR
(def diff (mrs/extract-diff-content 
            (mrs/get-merge-request-changes settings 
                                           (:project_id mr) 
                                           (:iid mr))))

;; 3. Run codebase-analyzer review on diff
;; (see codebase-analyzer integration)

;; 4. Post review as comment
(mrs/add-merge-request-note settings 
                            (:project_id mr) 
                            (:iid mr) 
                            review-markdown)
```

## License

Same as pyjama-tools
