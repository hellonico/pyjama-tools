(ns plane-client.core
  "Core HTTP client for Plane API"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [secrets.core :as secrets]
            [clojure.string :as str]))

;; ============================================================================
;; Configuration Loading
;; ============================================================================

(defn load-settings
  "Load Plane API settings using the secrets library.

  The secrets library automatically checks (in priority order):
  1. ./secrets.edn (project local)
  2. ./secrets.edn.enc (project encrypted)
  3. ~/secrets.edn (user home)
  4. ~/secrets.edn.enc (user home encrypted)
  5. Environment variables (PLANE__* prefix)

  Example secrets.edn:
  {:plane
   {:api-key \"plane_api_...\"
    :base-url \"https://api.plane.so\"  ; or your self-hosted instance
    :workspace-slug \"my-workspace\"}}  ; default workspace"
  []
  (secrets/require-secret! :plane))

(defn get-api-key
  "Extract API key from settings"
  [settings]
  (:api-key settings))

(defn get-base-url
  "Extract base URL from settings (defaults to Plane Cloud)"
  [settings]
  (or (:base-url settings) "https://api.plane.so"))

(defn get-workspace-slug
  "Extract default workspace slug from settings"
  [settings]
  (:workspace-slug settings))

;; ============================================================================
;; HTTP Request Helpers
;; ============================================================================

(defn make-headers
  "Create HTTP headers with authentication"
  [api-key]
  {"X-API-Key" api-key
   "Content-Type" "application/json"
   "Accept" "application/json"})

(defn build-url
  "Build full URL from base URL and path"
  [base-url path]
  (str base-url
       (when-not (.startsWith path "/") "/")
       path))

(defn parse-response
  "Parse HTTP response body as JSON"
  [response]
  (try
    (when-let [body (:body response)]
      (if (string? body)
        (json/parse-string body true)
        body))
    (catch Exception e
      (println "⚠️  Failed to parse response:" (.getMessage e))
      nil)))

;; ============================================================================
;; Core HTTP Methods
;; ============================================================================

(defn request
  "Make an authenticated HTTP request to the Plane API.

  Parameters:
  - settings: Plane settings map (from load-settings)
  - method: HTTP method (:get, :post, :patch, :delete)
  - path: API endpoint path (e.g., '/api/v1/workspaces/{slug}/projects/')
  - options: Optional map with:
    - :query-params - Query string parameters
    - :body - Request body (will be JSON encoded)
    - :workspace - Workspace slug (overrides default)

  Returns: Parsed response body (as Clojure map)"
  [settings method path & [{:keys [query-params body workspace] :as opts}]]
  (let [api-key (get-api-key settings)
        base-url (get-base-url settings)
        headers (make-headers api-key)
        full-path (if workspace
                    (clojure.string/replace path "{workspace_slug}" workspace)
                    path)
        url (build-url base-url full-path)
        request-opts (cond-> {:headers headers
                              :throw-exceptions false
                              :content-type :json
                              :accept :json}
                       query-params (assoc :query-params query-params)
                       body (assoc :body (json/generate-string body)))]

    (println "🌐" (clojure.string/upper-case (name method)) url)

    (try
      (let [response (case method
                       :get (http/get url request-opts)
                       :post (http/post url request-opts)
                       :patch (http/patch url request-opts)
                       :put (http/put url request-opts)
                       :delete (http/delete url request-opts)
                       (throw (ex-info "Unsupported HTTP method" {:method method})))
            status (:status response)]

        (cond
          ;; Success responses (2xx)
          (<= 200 status 299)
          (do
            (println "✓ Success (" status ")")
            {:success true
             :status status
             :data (parse-response response)})

          ;; Client errors (4xx)
          (<= 400 status 499)
          (do
            (println "✗ Client Error (" status ")")
            {:success false
             :status status
             :error :client-error
             :data (parse-response response)})

          ;; Server errors (5xx)
          (<= 500 status 599)
          (do
            (println "✗ Server Error (" status ")")
            {:success false
             :status status
             :error :server-error
             :data (parse-response response)})

          ;; Unknown
          :else
          (do
            (println "✗ Unknown Status (" status ")")
            {:success false
             :status status
             :error :unknown
             :data (parse-response response)})))

      (catch Exception e
        (println "✗ Request failed:" (.getMessage e))
        {:success false
         :error :exception
         :message (.getMessage e)
         :exception e}))))

(defn try-endpoints
  "Try multiple endpoint paths with automatic fallback.
  
  Attempts each path in order until one succeeds or all fail.
  This provides forward and backward compatibility."
  [settings method paths opts]
  (loop [remaining-paths paths
         last-response nil]
    (if (empty? remaining-paths)
      ;; All paths failed, return last error
      (or last-response
          {:success false
           :error :all-endpoints-failed
           :message "All endpoint paths failed"})

      (let [path (first remaining-paths)
            response (request settings method path opts)]

        (if (:success response)
          ;; Success! Return immediately
          response
          ;; Try next path on 404, otherwise return error
          (if (= 404 (:status response))
            (recur (rest remaining-paths) response)
            response))))))

(defn get-request
  "Make a GET request"
  [settings path & [opts]]
  (request settings :get path opts))

(defn post-request
  "Make a POST request"
  [settings path body & [opts]]
  (request settings :post path (assoc opts :body body)))

(defn patch-request
  "Make a PATCH request"
  [settings path body & [opts]]
  (request settings :patch path (assoc opts :body body)))

(defn delete-request
  "Make a DELETE request"
  [settings path & [opts]]
  (request settings :delete path opts))

;; ============================================================================
;; Pagination Support
;; ============================================================================

(defn paginate
  "Fetch all pages from a paginated endpoint.

  Parameters:
  - settings: Plane settings
  - path: API endpoint
  - opts: Options map (can include :per-page, :workspace, etc.)

  Returns: Vector of all results across all pages"
  [settings path & [opts]]
  (loop [cursor nil
         all-results []]
    (let [query-params (cond-> (:query-params opts {})
                         cursor (assoc :cursor cursor)
                         (:per-page opts) (assoc :per_page (:per-page opts)))
          response (get-request settings path (assoc opts :query-params query-params))]

      (if-not (:success response)
        ;; Error occurred, return what we have so far
        all-results

        ;; Success, accumulate results
        (let [data (:data response)
              results (:results data)
              next-cursor (:next data)
              accumulated (into all-results results)]

          (if next-cursor
            ;; More pages available
            (recur next-cursor accumulated)
            ;; Done
            accumulated))))))

(comment
  ;; Example usage in REPL

  ;; 1. Load settings
  (def settings (load-settings))

  ;; 2. Make a simple GET request
  (get-request settings "/api/v1/workspaces/my-workspace/projects/")

  ;; 3. Make a POST request
  (post-request settings
                "/api/v1/workspaces/my-workspace/projects/"
                {:name "New Project"
                 :identifier "NPR"
                 :description "Created via API"})

  ;; 4. Use pagination
  (paginate settings
            "/api/v1/workspaces/my-workspace/projects/{project_id}/work-items/"
            {:workspace "my-workspace"
             :per-page 50}))
