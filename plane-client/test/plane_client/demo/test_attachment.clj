(ns plane-client.demo.test-attachment
  "Test attachment operations using the working endpoints"
  (:require [plane-client.core :as plane]
            [plane-client.projects :as projects]
            [plane-client.work-items :as items]
            [plane-client.attachments :as att]))

(defn -main [& _args]
  (println "🧪 Testing Attachment Operations (Fixed Endpoints)")
  (println "==================================================\n")

  (let [settings (plane/load-settings)]
    (when-not settings
      (println "✗ Failed to load settings")
      (System/exit 1))

    ;; Find EDEMO project
    (let [existing-projects (projects/list-projects settings)
          demo-project (first (filter #(= "EDEMO" (:identifier %)) existing-projects))]

      (if-not demo-project
        (println "✗ No EDEMO project found")

        (let [project-id (:id demo-project)
              all-items (items/list-work-items settings project-id)]

          (println "📁 Project:" (:name demo-project))
          (println "   ID:" project-id)
          (println "\n📝 Work items:" (count all-items))

          (if (empty? all-items)
            (println "\n✗ No work items found - create one first")

            ;; Find EDEMO-11 (sequence_id 11)
            (let [target-item (first (filter #(= 11 (:sequence_id %)) all-items))]
              (if-not target-item
                (do
                  (println "\n✗ Could not find EDEMO-11")
                  (println "\nAvailable issues:")
                  (doseq [item all-items]
                    (println "  -" (str "EDEMO-" (:sequence_id item)) "-" (:name item))))

                (do
                  (println "\n🎯 Testing attachment operations on EDEMO-11:")
                  (println "   Title:" (:name target-item))
                  (println "   ID:" (:id target-item))
                  (println "   Sequence:" (:sequence_id target-item))

                  ;; List attachments using the NEW working endpoint
                  (println "\n📎 Listing attachments...")
                  (println "   Endpoint: /api/v1/.../issues/.../issue-attachments/")

                  (let [attachments (att/list-attachments settings
                                                          project-id
                                                          (:id target-item))]
                    (if (empty? attachments)
                      (println "\n⚠️  No attachments found")
                      (do
                        (println "\n✅ Found" (count attachments) "attachment(s):\n")
                        (doseq [[idx att] (map-indexed vector attachments)]
                          (let [attrs (:attributes att)
                                filename (:name attrs)
                                size (:size attrs)
                                file-type (:type attrs)]
                            (println (format "%d. %s" (inc idx) filename))
                            (println (format "   Size: %,d bytes" size))
                            (println "   Type:" file-type)
                            (println "   Asset path:" (:asset att))
                            (println "   Download URL:" (:asset_url att))
                            (println)))

                        ;; Download all attachments
                        (println "📥 Downloading all attachments to ./downloads/...")
                        (let [downloaded (att/download-all-attachments settings
                                                                       project-id
                                                                       (:id target-item)
                                                                       "downloads")]
                          (println "\n✨ Test complete!")
                          (when (> downloaded 0)
                            (println "   Check the ./downloads/ directory for files")))))))))))))))

(comment
  (-main))
