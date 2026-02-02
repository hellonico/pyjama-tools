(ns gitlab-client.core
  "GitLab API client for Clojure"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [secrets.core :as secrets]
            [clojure.string :as str]))

;; ============================================================================
;; Configuration
;; ============================================================================

(defn load-settings
  "Load GitLab settings from secrets.edn or environment variables.
  
  Expected settings:
  - :gitlab-url - GitLab instance URL (default: https://gitlab.com)
  - :gitlab-token - Personal access token (required at runtime)
  
  Returns settings map."
  []
  (let [url (or (secrets/get-secret :gitlab-url) "https://gitlab.com")
        ;; Use require-secret!! so it's only checked at runtime, not namespace load
        token (secrets/require-secret!! :gitlab-token)]
    {:url url
     :token token}))

;; ============================================================================
;; HTTP Request Helpers
;; ============================================================================

(defn- build-url
  "Build full API URL from base URL and path"
  [base-url path]
  (let [base (str/replace base-url #"/$" "")
        path (if (str/starts-with? path "/") path (str "/" path))]
    (str base "/api/v4" path)))

(defn request
  "Make HTTP request to GitLab API.
  
  Parameters:
  - settings: GitLab settings map
  - method: HTTP method (:get, :post, :put, :delete, :patch)
  - path: API path (e.g., /projects or /merge_requests)
  - opts: Optional map with:
    - :query-params - Query parameters
    - :body - Request body (will be JSON encoded)
  
  Returns: {:success true/false :data <response> :status <code> :error <error>}"
  [settings method path opts]
  (let [url (build-url (:url settings) path)
        headers {"PRIVATE-TOKEN" (:token settings)
                 "Content-Type" "application/json"}
        req-opts (cond-> {:headers headers
                          :throw-exceptions false
                          :as :json}
                   (:query-params opts) (assoc :query-params (:query-params opts))
                   (:body opts) (assoc :body (json/generate-string (:body opts))))]

    (println "🌐" (str/upper-case (name method)) url)

    (try
      (let [response (http/request (assoc req-opts :method method :url url))
            status (:status response)
            success? (<= 200 status 299)]

        (if success?
          (println "✓ Success (" status ")")
          (println "✗" (cond
                         (<= 400 status 499) "Client Error"
                         (<= 500 status 599) "Server Error"
                         :else "Error")
                   "(" status ")"))

        {:success success?
         :status status
         :data (:body response)
         :error (when-not success? :request-failed)})

      (catch Exception e
        (println "✗ Exception:" (.getMessage e))
        {:success false
         :error :exception
         :message (.getMessage e)}))))

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn get-current-user
  "Get information about the authenticated user.
  
  Returns user map with :id, :username, :name, :email, etc."
  [settings]
  (let [response (request settings :get "/user" {})]
    (when (:success response)
      (:data response))))

(comment
  ;; Example usage
  (def settings (load-settings))

  ;; Get current user
  (get-current-user settings))
