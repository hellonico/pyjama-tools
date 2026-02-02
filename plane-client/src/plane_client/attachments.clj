(ns plane-client.attachments
  "Attachment operations using the working endpoints discovered via browser debugging.
  
  Based on findings from browser Network tab inspection:
  - List: GET /api/v1/.../issues/{id}/issue-attachments/
  - Download: GET /api/v1/.../issues/{id}/issue-attachments/{att-id}/ (302 redirect to signed URL)
  - Upload: 3-step process (POST for credentials, POST to S3, PATCH to finalize)"
  (:require [plane-client.core :as core]
            [clj-http.client :as http]
            [clojure.java.io :as io])
  (:import [java.io File]))

(defn list-attachments
  "List all attachments for a work item.
  
  Uses the working endpoint: /api/v1/.../issues/{id}/issue-attachments/
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID  
  - work-item-id: Work item ID
  
  Returns: Vector of attachment maps with:
    - :id - Attachment ID
    - :asset - Asset path (e.g., 'workspace-id/file-hash-name.ext')
    - :attributes - Map with :name, :size, :type
    - :created_by, :updated_at, etc."
  [settings project-id work-item-id]
  (let [workspace (or (:workspace settings) (:workspace-slug settings))
        path (format "/api/v1/workspaces/%s/projects/%s/issues/%s/issue-attachments/"
                     workspace project-id work-item-id)
        response (core/request settings :get path {})]

    (if (:success response)
      (let [attachments (:data response)]
        (or attachments []))
      (do
        (println "✗ Error listing attachments:" (:error response))
        []))))

(defn download-attachment
  "Download an attachment to a local file.
  
  The download works by calling the individual attachment endpoint:
  GET /api/v1/.../issues/.../issue-attachments/{attachment-id}/
  
  This returns a 302 redirect to a pre-signed S3 URL that can be downloaded.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - attachment: Attachment map (from list-attachments)
  - output-file: Path to save the file
  
  Returns: true if successful, false otherwise"
  [settings project-id work-item-id attachment output-file]
  (let [attachment-id (:id attachment)
        filename (get-in attachment [:attributes :name])
        size (get-in attachment [:attributes :size])
        workspace (or (:workspace settings) (:workspace-slug settings))]

    (try
      (println (format "⬇️  Downloading: %s (%,d bytes)" filename size))

      ;; Call the individual attachment endpoint which returns a 302 redirect
      ;; to a pre-signed S3 URL
      (let [base-url (:base-url settings)
            api-key (core/get-api-key settings)
            attachment-url (format "%s/api/v1/workspaces/%s/projects/%s/issues/%s/issue-attachments/%s/"
                                   base-url workspace project-id work-item-id attachment-id)
            response (http/get attachment-url
                               {:headers (core/make-headers api-key)
                                :as :byte-array
                                :follow-redirects true})]  ; Follow the 302 redirect

        (if (= 200 (:status response))
          (do
            (io/copy (:body response) (io/file output-file))
            (println "   ✓ Saved to:" output-file)
            true)
          (do
            (println "   ✗ Download failed (HTTP" (:status response) ")")
            false)))

      (catch Exception e
        (println "   ✗ Error downloading:" (.getMessage e))
        false))))

(defn download-all-attachments
  "Download all attachments for a work item to a directory.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - output-dir: Directory to save attachments (will be created)
  
  Returns: Number of attachments successfully downloaded"
  [settings project-id work-item-id output-dir]
  (println "📥 Downloading all attachments...")

  ;; Create output directory
  (.mkdirs (io/file output-dir))

  (let [attachments (list-attachments settings project-id work-item-id)
        successful (atom 0)]

    (println (format "   Found %d attachment(s)" (count attachments)))

    (doseq [attachment attachments]
      (let [filename (get-in attachment [:attributes :name])
            output-file (str output-dir "/" filename)]
        (when (download-attachment settings project-id work-item-id attachment output-file)
          (swap! successful inc))))

    (println (format "\n📊 Downloaded %d of %d attachment(s)" @successful (count attachments)))
    @successful))

(defn upload-attachment
  "Upload a file as an attachment to a work item.
  
  This implements the 3-step upload flow discovered via browser debugging:
  1. POST /api/assets/v2/.../attachments/ to get upload credentials
  2. POST to S3/MinIO with multipart form data
  3. PATCH /api/assets/v2/.../attachments/{asset-id}/ to finalize
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID
  - work-item-id: Work item ID
  - file-path: Path to file to upload
  - filename: (Optional) Override filename to use instead of file's actual name
  
  Returns: Attachment map if successful, nil otherwise"
  ([settings project-id work-item-id file-path]
   (upload-attachment settings project-id work-item-id file-path nil))
  ([settings project-id work-item-id file-path custom-filename]
   (let [file (io/file file-path)
         filename (or custom-filename (.getName file))  ; Use custom filename if provided
         file-size (.length file)
         workspace (or (:workspace settings) (:workspace-slug settings))
         base-url (:base-url settings)
         api-key (core/get-api-key settings)]

     (if-not (.exists file)
       (do
         (println "✗ File not found:" file-path)
         nil)

       (try
         (println (format "📤 Uploading: %s (%,d bytes)" filename file-size))

         ;; Step 1: Request upload credentials
         (println "   1/3 Requesting upload credentials...")
         (let [;; Detect content type from file extension
               content-type (cond
                              (.endsWith filename ".png") "image/png"
                              (.endsWith filename ".jpg") "image/jpeg"
                              (.endsWith filename ".jpeg") "image/jpeg"
                              (.endsWith filename ".gif") "image/gif"
                              (.endsWith filename ".pdf") "application/pdf"
                              :else "application/octet-stream")

               creds-url (format "%s/api/v1/workspaces/%s/projects/%s/issues/%s/issue-attachments/"
                                 base-url workspace project-id work-item-id)
               creds-response (http/post creds-url
                                         {:headers (core/make-headers api-key)
                                          :content-type :json
                                          :form-params {:name filename
                                                        :size file-size
                                                        :type content-type}
                                          :as :json})]

           (if-not (= 200 (:status creds-response))
             (do
               (println "   ✗ Failed to get upload credentials (HTTP" (:status creds-response) ")")
               nil)

             (let [response-body (:body creds-response)
                   upload-data (:upload_data response-body)
                   asset-id (:asset_id response-body)
                   upload-url (:url upload-data)
                   form-fields (:fields upload-data)]

               (println "   ✓ Got upload credentials")
               (println (format "      Asset ID: %s" asset-id))

               ;; Step 2: Upload to S3/MinIO
               (println "   2/3 Uploading file to storage...")
               (let [;; Build multipart form with all S3 fields + file
                     multipart-data (concat
                                     ;; Add all the S3 signature fields
                                     (map (fn [[k v]] {:name (name k) :content v}) form-fields)
                                     ;; Add the file itself
                                     [{:name "file"
                                       :content file
                                       :filename filename}])

                     upload-response (http/post upload-url
                                                {:multipart multipart-data
                                                 :throw-exceptions false})]

                 (if-not (<= 200 (:status upload-response) 299)
                   (do
                     (println "   ✗ File upload failed (HTTP" (:status upload-response) ")")
                     nil)

                   (do
                     (println "   ✓ File uploaded successfully")

                     ;; Step 3: Finalize the attachment
                     (println "   3/3 Finalizing attachment...")
                     (let [finalize-url (format "%s/api/v1/workspaces/%s/projects/%s/issues/%s/issue-attachments/%s/"
                                                base-url workspace project-id work-item-id asset-id)
                           finalize-response (http/patch finalize-url
                                                         {:headers (core/make-headers api-key)
                                                          :content-type :json
                                                          :body "{}"
                                                          :as :json})]

                       (if-not (#{200 204} (:status finalize-response))
                         (do
                           (println "   ✗ Failed to finalize attachment (HTTP" (:status finalize-response) ")")
                           nil)
                         (do
                           (println "   ✓ Attachment uploaded successfully!")
                           (or (:body finalize-response) response-body))))))))))

         (catch Exception e
           (println "   ✗ Upload error:" (.getMessage e))
           nil))))))

(comment
  ;; Usage examples
  (require '[plane-client.core :as core])
  (require '[plane-client.attachments :as att])

  (def settings (core/load-settings))
  (def project-id "e9a526c9-3ac1-4b10-9437-fa46003ec55a")
  (def work-item-id "17f842ec-682e-483e-9b0b-ced8c9cc56cb")

  ;; List attachments
  (def attachments (att/list-attachments settings project-id work-item-id))

  ;; Download single attachment
  (att/download-attachment settings project-id work-item-id (first attachments) "./01.png")

  ;; Download all attachments
  (att/download-all-attachments settings project-id work-item-id "./attachments")

  ;; Upload attachment
  (att/upload-attachment settings project-id work-item-id "./test-image.png"))
